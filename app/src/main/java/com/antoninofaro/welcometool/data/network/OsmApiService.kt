package com.antoninofaro.welcometool.data.network

import com.antoninofaro.welcometool.data.model.OsmChangesetWrapper
import com.antoninofaro.welcometool.data.model.OsmUserWrapper
import retrofit2.http.GET
import retrofit2.http.Query

interface OsmApiService {

    @GET("api/0.6/changesets.json")
    suspend fun getRecentChangesets(
        @Query("bbox") bbox: String,
        @Query("time") time: String? = null,
        @Query("limit") limit: Int? = null
    ): OsmChangesetWrapper

    @GET("api/0.6/user/{id}.json")
    suspend fun getUserDetail(
        @retrofit2.http.Path("id") id: Long
    ): OsmUserWrapper

    @GET("api/0.6/changesets.json")
    suspend fun getUserChangesets(
        @Query("user") userId: Long
    ): OsmChangesetWrapper

    @GET("api/0.6/changesets.json")
    suspend fun getChangesetsByUsername(
        @Query("display_name") username: String,
        @Query("limit") limit: Int = 1
    ): OsmChangesetWrapper
}
