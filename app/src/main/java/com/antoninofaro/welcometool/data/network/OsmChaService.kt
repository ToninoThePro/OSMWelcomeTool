package com.antoninofaro.welcometool.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface OsmChaService {
    @GET("changesets/checked/")
    suspend fun getCheckedChangesets(
        @Query("users") username: String,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 100
    ): OsmChaResponse

    @GET("users/")
    suspend fun getCurrentUser(): OsmChaUserResponse

    @GET("changesets/{id}/")
    suspend fun getChangesetDetail(
        @retrofit2.http.Path("id") id: Long
    ): OsmChaChangesetDetailResponse
}

@Serializable
data class OsmChaChangesetDetailResponse(
    val id: Long,
    val reasons: List<OsmChaReason> = emptyList(),
    val check_user: String? = null,
    val checked: Boolean = false,
    val harmful: Boolean? = null,
    val is_suspect: Boolean = false,
    val create: Int = 0,
    val modify: Int = 0,
    val delete: Int = 0,
    val tags: List<OsmChaTag> = emptyList()
)

@Serializable
data class OsmChaReason(
    val name: String
)

@Serializable
data class OsmChaTag(
    val name: String
)

@Serializable
data class OsmChaResponse(
    val count: Int,
    val features: List<OsmChaFeature>
)

@Serializable
data class OsmChaUserResponse(
    val username: String
)

@Serializable
data class OsmChaFeature(
    val properties: OsmChaProperties
)

@Serializable
data class OsmChaProperties(
    @SerialName("check_user")
    val checkUser: String? = null,
    @SerialName("checked")
    val isChecked: Boolean = false,
    val harmful: Boolean? = null,
    @SerialName("is_suspect")
    val isSuspect: Boolean = false
)
