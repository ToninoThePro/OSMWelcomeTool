package com.antoninofaro.welcometool.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OsmUserWrapper(
    @SerialName("user")
    val user: OsmUser
)

@Serializable
data class OsmUsersWrapper(
    @SerialName("users")
    val users: List<OsmUserWrapper>
)

@Immutable
@Serializable
data class OsmUser(
    val id: Long,
    @SerialName("display_name")
    val displayName: String,
    @SerialName("account_created")
    val accountCreated: String,
    val description: String? = null,
    val img: UserImage? = null,
    val roles: List<String>? = null,
    val changesets: CountWrapper? = null,
    val traces: CountWrapper? = null,
)

@Serializable
data class UserImage(
    val href: String
)

@Serializable
data class CountWrapper(
    val count: Int
)

@Serializable
data class OsmChangesetWrapper(
    @SerialName("changesets")
    val changesets: List<OsmChangeset>
)

@Immutable
@Serializable
data class OsmChangeset(
    val id: Long,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("closed_at")
    val closedAt: String? = null,
    val open: Boolean = false,
    val user: String = "",
    val uid: Long = 0,
    @SerialName("min_lat")
    val minLat: Double? = null,
    @SerialName("min_lon")
    val minLon: Double? = null,
    @SerialName("max_lat")
    val maxLat: Double? = null,
    @SerialName("max_lon")
    val maxLon: Double? = null,
    @SerialName("num_changes")
    val numChanges: Int = 0,
    @SerialName("comments_count")
    val commentsCount: Int = 0,
    val tags: Map<String, String>? = null
)
