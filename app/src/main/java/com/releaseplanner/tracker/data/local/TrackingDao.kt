package com.releaseplanner.tracker.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackingDao {
    @Query("SELECT * FROM user_tracking")
    fun observeAll(): Flow<List<UserTrackingEntity>>

    @Query("SELECT * FROM user_tracking")
    suspend fun getAll(): List<UserTrackingEntity>

    @Query("SELECT * FROM user_tracking WHERE releaseId = :releaseId LIMIT 1")
    suspend fun getByReleaseId(releaseId: String): UserTrackingEntity?

    @Upsert
    suspend fun upsert(tracking: UserTrackingEntity)
}
