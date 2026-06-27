package com.antoninofaro.welcometool.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface NominatimApiService {

    @GET("search")
    suspend fun searchPlaces(
        @Query("q") query: String,
        @Query("format") format: String = "jsonv2",
        @Query("limit") limit: Int = 5,
        @Query("addressdetails") addressDetails: Int = 0
    ): List<NominatimPlace>
}

@Serializable
data class NominatimPlace(
    @SerialName("display_name")
    val displayName: String,
    @SerialName("boundingbox")
    val boundingBox: List<String>
)
