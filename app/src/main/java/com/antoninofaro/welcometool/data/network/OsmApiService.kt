package com.antoninofaro.welcometool.data.network

import com.antoninofaro.welcometool.data.model.OsmChangesetWrapper
import com.antoninofaro.welcometool.data.model.OsmUserWrapper
import com.antoninofaro.welcometool.data.model.OsmUsersWrapper
import com.antoninofaro.welcometool.data.model.SingleOsmChangesetWrapper
import retrofit2.http.GET
import retrofit2.http.Query

interface OsmApiService {

    @GET("api/0.6/changeset/{id}.json")
    suspend fun getChangesetById(
        @retrofit2.http.Path("id") id: Long
    ): SingleOsmChangesetWrapper

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

    @GET("api/0.6/users.json")
    suspend fun getUsersDetails(
        @Query("users") userIds: String
    ): OsmUsersWrapper

    @GET("api/0.6/changesets.json")
    suspend fun getUserChangesets(
        @Query("user") userId: Long? = null,
        @Query("bbox") bbox: String? = null,
        @Query("limit") limit: Int = 100,
        @Query("display_name") username: String? = null,
        @Query("time") time: String? = null
    ): OsmChangesetWrapper

    @GET("api/0.6/changesets.json")
    suspend fun getUserChangesetsAnywhere(
        @Query("user") userId: Long,
        @Query("limit") limit: Int = 100
    ): OsmChangesetWrapper

    @GET("api/0.6/changesets.json")
    suspend fun getChangesetsByUsername(
        @Query("display_name") username: String,
        @Query("limit") limit: Int = 1
    ): OsmChangesetWrapper

    @GET("api/0.6/changesets.json")
    suspend fun getChangesetsByIds(
        @Query("changesets") ids: String
    ): OsmChangesetWrapper

    @GET("api/0.6/changeset/{id}/download")
    suspend fun getChangesetDownload(
        @retrofit2.http.Path("id") id: Long
    ): okhttp3.ResponseBody
}
