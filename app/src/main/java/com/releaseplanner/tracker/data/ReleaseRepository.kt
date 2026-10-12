package com.releaseplanner.tracker.data

import androidx.room.withTransaction
import com.releaseplanner.tracker.data.local.ChangeEventEntity
import com.releaseplanner.tracker.data.local.ReleasePlannerDatabase
import com.releaseplanner.tracker.data.local.ReleaseUpdateEntity
import com.releaseplanner.tracker.data.local.UserTrackingEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
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

    suspend fun refresh(): SyncResult = refreshMutex.withLock { withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        var total = 0
        var newCount = 0
        var changedCount = 0
        val failedSources = mutableListOf<String>()
        val failureMessages = mutableListOf<String>()
        val events = mutableListOf<ChangeEventEntity>()

        val activeSources = preferences?.enabledSources() ?: sources
        val existingById = database.releaseUpdateDao().getAll().associateBy { it.id }.toMutableMap()
        val fetchedSources = fetchReleaseSources(activeSources, client::fetch)

        fetchedSources.forEach { (source, fetchedItems) ->
            runCatching {
                    val items = fetchedItems.getOrThrow()
                    val sourceExisting = if (source.schema == ReleaseSourceSchema.M365) {
                        existingById.values
                            .filter { it.sourceProduct == source.product }
                    } else {
                        emptyList()
                    }
                    val m365ExistingByProductId = sourceExisting
                        .filter { it.isM365Adapted() && it.productId.isNotBlank() }
                        .associateBy { it.productId }
                    val legacyExistingByTitle = sourceExisting
                        .filterNot { it.isM365Adapted() }
                        .groupBy { it.featureName.normalizedReleaseTitle() }
                        .mapValuesTo(mutableMapOf()) { (_, matches) -> matches.toMutableList() }

                    val updates = items.map { dto ->
                        val id = dto.stableId(source)
                        val existingByStableId = existingById[id]
                        val existingByProductId = if (existingByStableId == null) {
                            m365ExistingByProductId[dto.productId]
                        } else {
                            null
                        }
                        val existingByLegacyTitle = if (existingByStableId == null && existingByProductId == null) {
                            legacyExistingByTitle[dto.featureName.normalizedReleaseTitle()]
                                ?.removeFirstOrNull()
                        } else {
                            null
                        }
                        val existing = existingByStableId ?: existingByProductId ?: existingByLegacyTitle
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
                    updates.forEach { existingById[it.id] = it }
                    total += updates.size
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
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

        SyncResult(
            total = total,
            newCount = newCount,
            changedCount = changedCount,
            failedSources = failedSources,
            failureMessages = failureMessages,
            syncedAt = now,
            sourceCount = activeSources.size,
        )
    } }

    private companion object {
        val refreshMutex = Mutex()
    }

    private fun ReleaseUpdateEntity.isM365Adapted(): Boolean {
        return releasePlanId.startsWith("m365:", ignoreCase = true) ||
            snapshotId.startsWith("m365:", ignoreCase = true)
    }

    suspend fun debugFirstSource(): ReleaseApiDiagnostic {
        return client.debug(diagnosticSource(sources, preferences?.enabledSources() ?: sources))
    }

    suspend fun summaryMetrics(): ReleaseSummaryMetrics {
        val enabledSourceProducts = (preferences?.enabledSources() ?: sources).map { it.product }.toSet()
        val updates = database.releaseUpdateDao().getAll()
            .filter { it.sourceProduct in enabledSourceProducts }
        val trackingByReleaseId = database.trackingDao().getAll().associateBy { it.releaseId }
        return calculateReleaseSummaryMetrics(updates, trackingByReleaseId)
    }

    suspend fun clearSyncBadges() {
        database.releaseUpdateDao().clearSyncBadges()
    }

    suspend fun completeReleasedTasks(includePublicPreview: Boolean = false): Int = database.withTransaction {
        val trackingByReleaseId = database.trackingDao().getAll().associateBy { it.releaseId }
        val updates = releasedTasksToComplete(
            updates = database.releaseUpdateDao().getAll(),
            trackingByReleaseId = trackingByReleaseId,
            includePublicPreview = includePublicPreview,
        )
        val now = System.currentTimeMillis()
        updates.forEach { update ->
            val tracking = trackingByReleaseId[update.id] ?: UserTrackingEntity(releaseId = update.id)
            database.trackingDao().upsert(tracking.copy(isComplete = true, isSkipped = false, updatedAt = now))
        }
        updates.size
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

internal suspend fun fetchReleaseSources(
    sources: List<ReleaseSource>,
    fetch: suspend (ReleaseSource) -> List<ReleaseItemDto>,
    timeoutMillis: Long = 30_000,
): List<Pair<ReleaseSource, Result<List<ReleaseItemDto>>>> = coroutineScope {
    val semaphore = Semaphore(4)
    sources.map { source ->
        async {
            semaphore.withPermit {
                val result = runCatching {
                    withTimeoutOrNull(timeoutMillis) { fetch(source) }
                        ?: throw IOException("Request timed out after ${timeoutMillis / 1_000} seconds")
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                }
                source to result
            }
        }
    }.awaitAll()
}
