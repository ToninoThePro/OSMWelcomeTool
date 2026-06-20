package com.antoninofaro.welcometool.data.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface OsmChaService {
    @GET("changesets/")
    suspend fun getUserChangesets(
        @Query("users") username: String
    ): OsmChaResponse

    @GET("users/")
    suspend fun getCurrentUser(): OsmChaUserResponse
}

data class OsmChaResponse(
    val count: Int,
    val features: List<OsmChaFeature>
)

data class OsmChaUserResponse(
    val username: String
)

data class OsmChaFeature(
    val properties: OsmChaProperties
)

data class OsmChaProperties(
    @SerializedName("check_user")
    val checkUser: String? = null,
    @SerializedName("checked")
    val isChecked: Boolean = false,
    val harmful: Boolean? = null,
    @SerializedName("is_suspect")
    val isSuspect: Boolean = false
)