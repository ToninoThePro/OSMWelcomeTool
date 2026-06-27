package com.antoninofaro.welcometool.data.repository

import com.antoninofaro.welcometool.data.model.Result
import com.antoninofaro.welcometool.data.model.log
import com.antoninofaro.welcometool.data.model.safeApiCall
import com.antoninofaro.welcometool.data.network.OsmChaService
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed class VerifyTokenResult {
    data class Success(val username: String) : VerifyTokenResult()
    data class Invalid(val message: String) : VerifyTokenResult()
    data class Error(val message: String) : VerifyTokenResult()
}

@Singleton
class OsmChaRepository @Inject constructor(
    private val osmChaService: OsmChaService,
    private val settingsRepository: SettingsRepository
) {

    suspend fun getUserOsmChaStats(username: String): Result<Pair<Int, Int>> {
        val result = safeApiCall {
            val limit = settingsRepository.settingsFlow.first().osmchaChangesetsLimit
            val pageSize = minOf(limit.coerceIn(1, 500), 200)
            var page = 1
            var likes = 0
            var dislikes = 0

            while (true) {
                val response = osmChaService.getCheckedChangesets(username, page, pageSize)
                for (f in response.features) {
                    if (f.properties.harmful == true) dislikes++ else likes++
                }
                if (response.features.size < pageSize) break
                if (likes + dislikes >= limit) break
                page++
            }
            Pair(likes, dislikes)
        }
        return result.log(
            errorMsg = "OSMCha request failed for $username",
            successMsg = { "OSMCha $username: ${it.first}L/${it.second}D" }
        )
    }

    suspend fun verifyToken(): Result<String> = safeApiCall {
        val response = osmChaService.getCurrentUser()
        response.username
    }

    suspend fun verifyTokenDetailed(): VerifyTokenResult {
        return try {
            val response = osmChaService.getCurrentUser()
            VerifyTokenResult.Success(response.username)
        } catch (e: HttpException) {
            if (e.code() == 401) {
                VerifyTokenResult.Invalid(e.message() ?: "Token non valido")
            } else {
                VerifyTokenResult.Error(e.message() ?: "Errore del server")
            }
        } catch (e: IOException) {
            VerifyTokenResult.Error(e.message ?: "Errore di connessione")
        } catch (e: Exception) {
            VerifyTokenResult.Error(e.message ?: "Errore sconosciuto")
        }
    }
}