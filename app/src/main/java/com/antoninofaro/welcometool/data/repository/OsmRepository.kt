package com.antoninofaro.welcometool.data.repository

import com.antoninofaro.welcometool.data.model.OsmChangeset
import com.antoninofaro.welcometool.data.model.OsmUser
import com.antoninofaro.welcometool.data.model.Result
import com.antoninofaro.welcometool.data.model.safeApiCall
import com.antoninofaro.welcometool.data.network.OsmApiService
import com.antoninofaro.welcometool.utils.Constants
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository handling interactions with the OpenStreetMap API.
 * Uses [safeApiCall] to capture and return standard [Result] objects, preventing exceptions from propagating up.
 */
@Singleton
class OsmRepository @Inject constructor(
    private val apiService: OsmApiService
) {

    /**
     * Fetches the most recent changesets within a specified bounding box and optional time range.
     *
     * @param bbox Bounding box string matching "min_lon,min_lat,max_lon,max_lat". Defaults to Italy.
     * @param timeRange Optional comma-separated time range to limit results, formatted as "start_time,end_time".
     * @param limit Maximum number of changesets to return.
     * @return A [Result] containing a list of [OsmChangeset] objects if successful, or an error state.
     */
    suspend fun fetchRecentChangesets(
        bbox: String = Constants.ITALY_BBOX,
        timeRange: String? = null,
        limit: Int? = null
    ): Result<List<OsmChangeset>> {
        return safeApiCall {
            val response = apiService.getRecentChangesets(bbox, timeRange, limit)
            response.changesets
        }.also { result ->
            if (result.isError) {
                Timber.e(result.exceptionOrNull(), "Error fetching recent changesets for bbox: $bbox")
            } else {
                Timber.d("Successfully fetched ${result.getOrNull()?.size ?: 0} changesets")
            }
        }
    }

    suspend fun fetchUserDetail(userId: Long): Result<OsmUser> {
        return safeApiCall {
            val response = apiService.getUserDetail(userId)
            response.user
        }.also { result ->
            if (result.isError) {
                Timber.e(result.exceptionOrNull(), "Error fetching user detail for userId: $userId")
            } else {
                Timber.d("Successfully fetched user: ${result.getOrNull()?.displayName}")
            }
        }
    }

    suspend fun fetchUsersDetails(userIds: List<Long>): Result<List<OsmUser>> {
        if (userIds.isEmpty()) return Result.Success(emptyList())
        return safeApiCall {
            val response = apiService.getUsersDetails(userIds.joinToString(","))
            response.users.map { it.user }
        }.also { result ->
            if (result.isError) {
                Timber.e(result.exceptionOrNull(), "Error fetching users details for ids: $userIds")
            } else {
                Timber.d("Successfully fetched ${result.getOrNull()?.size ?: 0} users details")
            }
        }
    }



    suspend fun fetchUserChangesets(userId: Long, limit: Int = 100): Result<List<OsmChangeset>> {
        return safeApiCall {
            val response = apiService.getUserChangesets(userId, limit)
            response.changesets.sortedByDescending { it.createdAt }
        }.also { result ->
            if (result.isError) {
                Timber.e(result.exceptionOrNull(), "Error fetching changesets for userId: $userId")
            } else {
                Timber.d("Successfully fetched ${result.getOrNull()?.size ?: 0} changesets for user $userId")
            }
        }
    }

    suspend fun searchUserByUsername(username: String): Result<OsmUser> {
        return safeApiCall {
            val changesetResponse = apiService.getChangesetsByUsername(username)
            val uid = changesetResponse.changesets.firstOrNull()?.uid
                ?: throw Exception("User not found or no changesets available for: $username")
            val userResponse = apiService.getUserDetail(uid)
            userResponse.user
        }.also { result ->
            if (result.isError) {
                Timber.e(result.exceptionOrNull(), "Error searching user by username: $username")
            } else {
                Timber.d("Successfully found user: ${result.getOrNull()?.displayName}")
            }
        }
    }
}
