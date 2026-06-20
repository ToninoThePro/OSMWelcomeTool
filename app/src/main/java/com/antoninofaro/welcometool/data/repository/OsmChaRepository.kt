package com.antoninofaro.welcometool.data.repository

import com.antoninofaro.welcometool.data.model.Result
import com.antoninofaro.welcometool.data.model.safeApiCall
import com.antoninofaro.welcometool.data.network.OsmChaService
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OsmChaRepository @Inject constructor(
    private val osmChaService: OsmChaService,
    private val settingsRepository: SettingsRepository
) {

    suspend fun getUserOsmChaStats(username: String): Pair<Int, Int> {
        return try {
            val changesetsLimit = settingsRepository.settingsFlow.first().osmchaChangesetsLimit
            val response = osmChaService.getUserChangesets(username = username)
            val reviewed = response.features.filter {
                it.properties.isChecked || it.properties.harmful != null
            }.take(changesetsLimit)
            val likes = reviewed.count { it.properties.isChecked && it.properties.harmful != true }
            val dislikes = reviewed.count { it.properties.harmful == true }
            Timber.d("OSMCha %s: likes=%d, dislikes=%d", username, likes, dislikes)
            Pair(likes, dislikes)
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 401 || e.code() == 403)
                Timber.e("OSMCha auth error for %s: %d", username, e.code())
            else
                Timber.e(e, "OSMCha HTTP %d for %s", e.code(), username)
            Pair(0, 0)
        } catch (e: Exception) {
            Timber.e(e, "OSMCha failed for %s", username)
            Pair(0, 0)
        }
    }

    suspend fun verifyToken(): Result<String> = safeApiCall {
        val response = osmChaService.getCurrentUser()
        response.username
    }
}