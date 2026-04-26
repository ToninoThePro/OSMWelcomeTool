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
                nominatimApiService.searchPlaces(query)
                    .mapNotNull { place ->
                        val bbox = place.boundingBox.toOsmBboxOrNull() ?: return@mapNotNull null
                        MonitoringArea(
                            name = place.displayName,
                            bbox = bbox
                        )
                    }
            }.also { result ->
                if (result.isError) {
                    Timber.e(result.exceptionOrNull(), "Errore ricerca aree Nominatim: $query")
                }
            }
        }
    }

    // Nominatim returns [south, north, west, east], OSM expects west,south,east,north.
    private fun List<String>.toOsmBboxOrNull(): String? {
        if (size < 4) return null
        val south = getOrNull(0)?.toDoubleOrNull() ?: return null
        val north = getOrNull(1)?.toDoubleOrNull() ?: return null
        val west = getOrNull(2)?.toDoubleOrNull() ?: return null
        val east = getOrNull(3)?.toDoubleOrNull() ?: return null
        return "$west,$south,$east,$north"
    }
}

