package com.antoninofaro.welcometool.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "changesets")
data class ChangesetEntity(
    @PrimaryKey
    val id: Long,
    val bbox: String,
    val createdAt: String,
    val closedAt: String?,
    val open: Boolean,
    val user: String,
    val uid: Long,
    val minLat: Double?,
    val minLon: Double?,
    val maxLat: Double?,
    val maxLon: Double?,
    val numChanges: Int,
    val commentsCount: Int,
    val tagsJson: String?,
    val osmchaScore: Int? = null,
    val osmchaIsSuspect: Boolean = false,
    val osmchaHarmful: Boolean? = null,
    val osmchaReasonsJson: String? = null,
    val createCount: Int = 0,
    val modifyCount: Int = 0,
    val deleteCount: Int = 0,
    val isOsmchaChecked: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "etag")
    val etag: String? = null,
    @ColumnInfo(name = "last_modified")
    val lastModified: String? = null
) {
    fun toOsmChangeset(): com.antoninofaro.welcometool.data.model.OsmChangeset {
        val tagsMap = try {
            if (!tagsJson.isNullOrBlank()) {
                kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(tagsJson)
            } else null
        } catch (_: Exception) {
            null
        }
        return com.antoninofaro.welcometool.data.model.OsmChangeset(
            id = id,
            createdAt = createdAt,
            closedAt = closedAt,
            open = open,
            user = user,
            uid = uid,
            minLat = minLat,
            minLon = minLon,
            maxLat = maxLat,
            maxLon = maxLon,
            numChanges = numChanges,
            changesCount = numChanges,
            commentsCount = commentsCount,
            tags = tagsMap,
            etag = etag,
            lastModified = lastModified
        )
    }
}
