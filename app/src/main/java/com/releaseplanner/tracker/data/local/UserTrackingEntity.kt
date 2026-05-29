package com.releaseplanner.tracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_tracking")
data class UserTrackingEntity(
    @PrimaryKey val releaseId: String,
    val isComplete: Boolean = false,
    val isSkipped: Boolean = false,
    val isSaved: Boolean = false,
    val isHidden: Boolean = false,
    val priority: String = "Normal",
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)
