package com.releaseplanner.tracker.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "change_events")
data class ChangeEventEntity(
    @PrimaryKey val id: String,
    val releaseId: String,
    val type: String,
    val title: String,
    val product: String,
    val createdAt: Long,
)
