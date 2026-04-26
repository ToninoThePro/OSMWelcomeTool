package com.antoninofaro.welcometool.data.repository

import com.antoninofaro.welcometool.data.model.OsmChangeset
import com.antoninofaro.welcometool.data.model.OsmUser
import com.antoninofaro.welcometool.data.model.Result
import com.antoninofaro.welcometool.data.model.safeApiCall
import com.antoninofaro.welcometool.data.network.OsmApiService
import com.antoninofaro.welcometool.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        return withContext(Dispatchers.IO) {
            safeApiCall {
                val response = apiService.getRecentChangesets(bbox, timeRange, limit)
                response.changesets
            }.also { result ->
                if (result.isError) {
                    Timber.e(
                        result.exceptionOrNull(),
                        "Error fetching recent changesets for bbox: $bbox"
                    )
                } else {
                    Timber.d("Successfully fetched ${result.getOrNull()?.size ?: 0} changesets")
                }
            }
        }
    }

    /**
     * Retrieves detailed profile information for a specific user ID.
     *
     * @param userId The unique numeric identifier of the OSM user.
     * @return A [Result] containing the [OsmUser] profile data, including creation date and basic stats.
     */
    suspend fun fetchUserDetail(userId: Long): Result<OsmUser> {
        return withContext(Dispatchers.IO) {
            safeApiCall {
                val response = apiService.getUserDetail(userId)
                response.user
            }.also { result ->
                if (result.isError) {
                    Timber.e(
                        result.exceptionOrNull(),
                        "Error fetching user detail for userId: $userId"
                    )
                } else {
                    Timber.d("Successfully fetched user: ${result.getOrNull()?.displayName}")
                }
            }
        }
    }

    /**
     * Fetches the changeset history for a specific user ID, sorting them by creation date in descending order.
     *
     * @param userId The unique numeric identifier of the OSM user.
     * @return A [Result] containing a list of the user's [OsmChangeset]s, effectively acting as an edit history.
     */
    suspend fun fetchUserChangesets(userId: Long): Result<List<OsmChangeset>> {
        return withContext(Dispatchers.IO) {
            safeApiCall {
                val response = apiService.getUserChangesets(userId)
                // Ordina per data di creazione decrescente
                response.changesets.sortedByDescending { it.createdAt }
            }.also { result ->
                if (result.isError) {
                    Timber.e(
                        result.exceptionOrNull(),
                        "Error fetching changesets for userId: $userId"
                    )
                } else {
                    Timber.d("Successfully fetched ${result.getOrNull()?.size ?: 0} changesets for user $userId")
                }
            }
        }
    }

    /**
     * Searches for a user strictly by their exact display username.
     * This method resolves the username to a UID by performing an initial changeset query,
     * and subsequently fetches the full user details using that UID.
     *
     * @param username The exact textual display name of the OSM user.
     * @return A [Result] carrying the corresponding [OsmUser] if the account is found, else a failure Result.
     */
    suspend fun searchUserByUsername(username: String): Result<OsmUser> {
        return withContext(Dispatchers.IO) {
            safeApiCall {
                // Fetch changesets by username to find the UID
                val changesetResponse = apiService.getChangesetsByUsername(username)
                val uid = changesetResponse.changesets.firstOrNull()?.uid
                    ?: throw Exception("Utente non trovato o nessun changeset disponibile per: $username")

                // Fetch user details using the UID
                val userResponse = apiService.getUserDetail(uid)
                userResponse.user
            }.also { result ->
                if (result.isError) {
                    Timber.e(
                        result.exceptionOrNull(),
                        "Error searching user by username: $username"
                    )
                } else {
                    Timber.d("Successfully found user: ${result.getOrNull()?.displayName}")
                }
            }
        }
    }
}
