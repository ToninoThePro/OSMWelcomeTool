package com.antoninofaro.welcometool.data.repository

import com.antoninofaro.welcometool.data.model.Result
import com.antoninofaro.welcometool.data.model.safeApiCall
import com.antoninofaro.welcometool.data.network.NominatimApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NominatimRepository @Inject constructor(
    private val nominatimApiService: NominatimApiService
) {

    suspend fun searchAreas(query: String): Result<List<MonitoringArea>> {
        if (query.isBlank()) return Result.Success(emptyList())

        return withContext(Dispatchers.IO) {
            safeApiCall {
                val apiResults = nominatimApiService.searchPlaces(query)
                    .mapNotNull { place ->
                        val bbox = place.boundingBox.toOsmBboxOrNull() ?: return@mapNotNull null
                        MonitoringArea(
                            name = place.displayName,
                            bbox = bbox
                        )
                    }

                val trimmed = query.trim().lowercase()
                val isGlobalQuery = trimmed in setOf("world", "mondo", "earth", "globo", "global", "pianeta", "tutto")

                if (isGlobalQuery && apiResults.none { it.bbox == com.antoninofaro.welcometool.utils.Constants.WORLD_BBOX }) {
                    listOf(MonitoringArea(com.antoninofaro.welcometool.utils.Constants.DEFAULT_AREA_NAME, com.antoninofaro.welcometool.utils.Constants.WORLD_BBOX)) + apiResults
                } else {
                    apiResults
                }
            }.also { result ->
                if (result.isError) {
                    Timber.e(result.exceptionOrNull(), "Errore ricerca aree Nominatim: $query")
                }
            }
        }
    }

    // Nominatim returns [south, north, west, east], GeoBox converts to west,south,east,north.
    private fun List<String>.toOsmBboxOrNull(): String? {
        return try {
            com.antoninofaro.welcometool.utils.GeoBox.fromNominatim(this).toOsmParam()
        } catch (_: Exception) {
            null
        }
    }
}

