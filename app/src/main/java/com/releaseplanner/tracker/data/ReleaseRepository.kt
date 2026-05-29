package com.releaseplanner.tracker.data

import com.releaseplanner.tracker.data.local.ChangeEventEntity
import com.releaseplanner.tracker.data.local.ReleasePlannerDatabase
import com.releaseplanner.tracker.data.local.UserTrackingEntity
import java.util.UUID

class ReleaseRepository(
    private val database: ReleasePlannerDatabase,
    private val client: ReleasePlannerClient = ReleasePlannerClient(),
    private val sources: List<ReleaseSource> = DefaultReleaseSources.all,
    private val preferences: ReleasePreferences? = null,
) {
    val updates = database.releaseUpdateDao().observeAll()
    val tracking = database.trackingDao().observeAll()
    val recentEvents = database.changeEventDao().observeRecent()

    suspend fun isEmpty(): Boolean = database.releaseUpdateDao().count() == 0

    suspend fun refresh(): SyncResult {
        val now = System.currentTimeMillis()
        var total = 0
        var newCount = 0
        var changedCount = 0
        val failedSources = mutableListOf<String>()
        val failureMessages = mutableListOf<String>()
        val events = mutableListOf<ChangeEventEntity>()

        val activeSources = preferences?.enabledSources() ?: sources

        activeSources.forEach { source ->
            runCatching { client.fetch(source) }
                .onSuccess { items ->
                    val updates = items.map { dto ->
                        val id = dto.stableId(source)
                        val existing = database.releaseUpdateDao().getById(id)
                        val entity = dto.toEntity(source, existing, now)
                        val isNewlyDetected = existing == null
                        val isChangedNow = existing != null && existing.contentHash != entity.contentHash

                        when {
                            isNewlyDetected -> {
                                newCount += 1
                                events += entity.toChangeEvent("New", now)
                            }
                            isChangedNow -> {
                                changedCount += 1
                                events += entity.toChangeEvent("Changed", now)
                            }
                        }
                        entity
                    }
                    database.releaseUpdateDao().upsertAll(updates)
                    total += updates.size
                }
                .onFailure { error ->
                    failedSources += source.product
                    failureMessages += "${source.product}: ${error.message ?: error::class.simpleName.orEmpty()}"
                }
        }

        if (events.isNotEmpty()) {
            database.changeEventDao().upsertAll(events)
        }

        if (failedSources.isNotEmpty()) {
            preferences?.recordApiFailure()
        }

        return SyncResult(
            total = total,
            newCount = newCount,
            changedCount = changedCount,
            failedSources = failedSources,
            failureMessages = failureMessages,
            syncedAt = now,
            sourceCount = activeSources.size,
        )
    }

    suspend fun debugFirstSource(): ReleaseApiDiagnostic {
        return client.debug((preferences?.enabledSources() ?: sources).firstOrNull() ?: sources.first())
    }

    suspend fun clearSyncBadges() {
        database.releaseUpdateDao().clearSyncBadges()
    }

    suspend fun updateTracking(
        releaseId: String,
        transform: (UserTrackingEntity) -> UserTrackingEntity,
    ) {
        val current = database.trackingDao().getByReleaseId(releaseId) ?: UserTrackingEntity(releaseId = releaseId)
        database.trackingDao().upsert(transform(current).copy(updatedAt = System.currentTimeMillis()))
    }

    private fun com.releaseplanner.tracker.data.local.ReleaseUpdateEntity.toChangeEvent(
        type: String,
        now: Long,
    ): ChangeEventEntity {
        return ChangeEventEntity(
            id = UUID.randomUUID().toString(),
            releaseId = this.id,
            type = type,
            title = featureName,
            product = product,
            createdAt = now,
        )
    }
}

data class SyncResult(
    val total: Int,
    val newCount: Int,
    val changedCount: Int,
    val failedSources: List<String>,
    val failureMessages: List<String>,
    val syncedAt: Long,
    val sourceCount: Int,
) {
    val hasChanges: Boolean = newCount > 0 || changedCount > 0
}
