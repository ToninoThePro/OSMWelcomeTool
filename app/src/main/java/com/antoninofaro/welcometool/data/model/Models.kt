package com.antoninofaro.welcometool.data.model

import com.google.gson.annotations.SerializedName
import androidx.compose.runtime.Immutable

data class OsmUserWrapper(
    @SerializedName("user")
    val user: OsmUser
)

@Immutable
data class OsmUser(
    val id: Long,
    @SerializedName("display_name")
    val displayName: String,
    @SerializedName("account_created")
    val accountCreated: String,
    val description: String?,
    val img: UserImage?,
    val roles: List<String>?,
    val changesets: CountWrapper?,
    val traces: CountWrapper?,
)

data class UserImage(
    val href: String
)

data class CountWrapper(
    val count: Int
)

data class OsmChangesetWrapper(
    @SerializedName("changesets")
    val changesets: List<OsmChangeset>
)

@Immutable
data class OsmChangeset(
    val id: Long,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("closed_at")
    val closedAt: String,
    val open: Boolean,
    val user: String,
    val uid: Long,
    @SerializedName("min_lat")
    val minLat: Double,
    @SerializedName("min_lon")
    val minLon: Double,
    @SerializedName("max_lat")
    val maxLat: Double,
    @SerializedName("max_lon")
    val maxLon: Double,
    @SerializedName("num_changes")
    val numChanges: Int,
    @SerializedName("comments_count")
    val commentsCount: Int,
    val tags: Map<String, String>?
)
