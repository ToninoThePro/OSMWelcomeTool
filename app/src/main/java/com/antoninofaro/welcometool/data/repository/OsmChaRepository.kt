package com.antoninofaro.welcometool.data.repository

import com.antoninofaro.welcometool.data.network.OsmChaService
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

interface OsmChaRepository {
    suspend fun getUserOsmChaStats(username: String): Pair<Int, Int>
}

@Singleton
class OsmChaRepositoryImpl @Inject constructor(
    private val osmChaService: OsmChaService,
    private val settingsRepository: SettingsRepository
) : OsmChaRepository {

    /**
     * Fetches changeset statistics for a specific user.
     * Returns a Pair where:
     * - first: number of "Likes" (isChecked = true AND harmful = false)
     * - second: number of "Dislikes" (harmful = true)
     *
     * Note: API doesn't support combined filters, so we fetch all changesets
     * and filter client-side.
     */
    override suspend fun getUserOsmChaStats(username: String): Pair<Int, Int> {

        return try {
            Timber.d("OSMCha request start: user=%s", username)

            // Get the changeset limit from settings
            val changesetsLimit = settingsRepository.settingsFlow.first().osmchaChangesetsLimit
            Timber.d("OSMCha changesets limit: %d", changesetsLimit)

            // Fetch all changesets for the user
            val response = osmChaService.getUserChangesets(username = username)

            Timber.d(
                "OSMCha response received: count=%d, features size=%d",
                response.count,
                response.features.size
            )

            // Filter to keep only changesets with review (checked or harmful)
            val reviewedChangesets = response.features.filter { feature ->
                feature.properties.isChecked || feature.properties.harmful != null
            }

            Timber.d(
                "OSMCha reviewed changesets: %d out of %d",
                reviewedChangesets.size,
                response.features.size
            )

            // Limit to the configured amount
            val limitedChangesets = reviewedChangesets.take(changesetsLimit)

            // Log first few features for debugging
            limitedChangesets.take(3).forEachIndexed { index, feature ->
                Timber.d(
                    "Feature $index: isChecked=%s, harmful=%s, checkUser=%s",
                    feature.properties.isChecked,
                    feature.properties.harmful,
                    feature.properties.checkUser
                )
            }

            // Filter client-side to count likes and dislikes
            val likes = limitedChangesets.count { feature ->
                feature.properties.isChecked && feature.properties.harmful != true
            }
            val dislikes = limitedChangesets.count { feature ->
                feature.properties.harmful == true
            }

            Timber.d(
                "OSMCha stats computed: user=%s, likes=%d, dislikes=%d, reviewed=%d, limited=%d",
                username, likes, dislikes, reviewedChangesets.size, limitedChangesets.size
            )
            Pair(likes, dislikes)
        } catch (e: retrofit2.HttpException) {
            // Gestione specifica degli errori HTTP (es. Token invalido)
            if (e.code() == 401 || e.code() == 403) {
                Timber.e(
                    "Errore di Autenticazione: Token OSMCha NON VALIDO o scaduto per l'utente %s",
                    username
                )
            } else {
                Timber.e(
                    e,
                    "Errore HTTP %d durante la richiesta OSMCha per l'utente %s",
                    e.code(),
                    username
                )
            }
            Pair(0, 0)
        } catch (e: Exception) {
            // Gestione errori di rete generici (es. timeout, no internet)
            Timber.e(e, "OSMCha request failed (Network/Other): user=%s", username)
            Pair(0, 0)
        }
    }
}