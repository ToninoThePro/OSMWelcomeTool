package com.antoninofaro.welcometool.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "area_sync_state")
data class AreaSyncStateEntity(
    @PrimaryKey val bbox: String,
    val forwardCursorId: Long = 0L,
    val forwardCursorDate: String = "",
    val backwardCursorDate: String? = null,
    val isHistoricalExhausted: Boolean = false,
    val lastPhaseBTimeMs: Long = 0L,
    val lastSyncStartTimeMs: Long = 0L
)
