package com.releaseplanner.tracker.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ChangeEventDao {
    @Query("SELECT * FROM change_events ORDER BY createdAt DESC LIMIT 100")
    fun observeRecent(): Flow<List<ChangeEventEntity>>

    @Upsert
    suspend fun upsertAll(events: List<ChangeEventEntity>)
}
