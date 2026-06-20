package com.antoninofaro.welcometool.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "user_area_activity",
    primaryKeys = ["bbox", "userId"],
    indices = [
        Index(value = ["lastChangesetDate"])
    ]
)
data class UserAreaActivityEntity(
    @ColumnInfo(index = true)
    val bbox: String,
    val userId: Long,
    val lastChangesetDate: String,
    val lastChangesetId: Long,
    val lastUpdated: Long = System.currentTimeMillis()
)
