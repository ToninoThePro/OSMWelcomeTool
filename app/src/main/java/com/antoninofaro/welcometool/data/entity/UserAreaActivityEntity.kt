package com.antoninofaro.welcometool.data.entity

import androidx.room.Entity

@Entity(
    tableName = "user_area_activity",
    primaryKeys = ["bbox", "userId"]
)
data class UserAreaActivityEntity(
    val bbox: String,
    val userId: Long,
    val lastChangesetDate: String,
    val lastChangesetId: Long,
    val lastUpdated: Long = System.currentTimeMillis()
)
