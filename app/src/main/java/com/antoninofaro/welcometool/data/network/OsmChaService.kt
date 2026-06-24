package com.antoninofaro.welcometool.data.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
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
}

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
