package com.releaseplanner.tracker.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReleaseUpdateDao {
    @Query("SELECT * FROM release_updates ORDER BY featureName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ReleaseUpdateEntity>>

    @Query("SELECT * FROM release_updates WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ReleaseUpdateEntity?

    @Query("SELECT COUNT(*) FROM release_updates")
    suspend fun count(): Int

    @Upsert
    suspend fun upsertAll(updates: List<ReleaseUpdateEntity>)

    @Query("UPDATE release_updates SET isNew = 0, isChanged = 0")
    suspend fun clearSyncBadges()
}
