package com.antoninofaro.welcometool.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "changesets")
data class ChangesetEntity(
    @PrimaryKey
    val id: Long,
    val createdAt: String,
    val closedAt: String,
    val open: Boolean,
    val user: String,
    val uid: Long,
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double,
    val numChanges: Int,
    val commentsCount: Int,
    // Tags can be complex to store directly, maybe ignore for list or use TypeConverter.
    // simpler to skip for now or store as JSON string if needed.
    // For listing, usually we don't need tags immediately.
)

