package com.antoninofaro.welcometool.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.antoninofaro.welcometool.utils.Constants
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val darkMode: Boolean = false,
    val autoRefresh: Boolean = true,
    val autoRefreshInterval: Int = 30, // minuti
    val defaultBBox: String = Constants.ITALY_BBOX,
    val defaultAreaName: String = Constants.DEFAULT_AREA_NAME,
    val showNotifications: Boolean = true,
    val minChangesetsFilter: Int = 0,
    // TODO: collegare questa flag al comportamento runtime della cache utente/rete.
    val cacheEnabled: Boolean = true,
    val osmchaToken: String = "",
    val osmchaChangesetsLimit: Int = 100, // numero massimo di changeset da controllare
    val lastKnownChangesetId: Long = 0L,
    val monitoringAreas: List<MonitoringArea> = defaultMonitoringAreas(),
    val debugLogsEnabled: Boolean = false // Cattura log Timber in memoria
)

data class MonitoringArea(
    val name: String,
    val bbox: String
)

private fun defaultMonitoringAreas(): List<MonitoringArea> = listOf(
    MonitoringArea(Constants.DEFAULT_AREA_NAME, Constants.ITALY_BBOX)
)

@Singleton
class SettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val secureTokenStorage: SecureTokenStorage
) {
    private val dataStore = context.dataStore
    private val gson = Gson()

    companion object {
        private val DARK_MODE = booleanPreferencesKey("dark_mode")
        private val AUTO_REFRESH = booleanPreferencesKey("auto_refresh")
        private val AUTO_REFRESH_INTERVAL = intPreferencesKey("auto_refresh_interval")
        private val DEFAULT_BBOX = stringPreferencesKey("default_bbox")
        private val DEFAULT_AREA_NAME = stringPreferencesKey("default_area_name")
        private val SHOW_NOTIFICATIONS = booleanPreferencesKey("show_notifications")
        private val MIN_CHANGESETS_FILTER = intPreferencesKey("min_changesets_filter")
        private val CACHE_ENABLED = booleanPreferencesKey("cache_enabled")
        private val OSMCHA_TOKEN = stringPreferencesKey("osmcha_token")
        private val OSMCHA_CHANGESETS_LIMIT = intPreferencesKey("osmcha_changesets_limit")
        private val LAST_KNOWN_CHANGESET_ID = longPreferencesKey("last_known_changeset_id")
        private val MONITORING_AREAS_JSON = stringPreferencesKey("monitoring_areas_json")
        private val DEBUG_LOGS_ENABLED = booleanPreferencesKey("debug_logs_enabled")
    }

    val settingsFlow: Flow<AppSettings> = dataStore.data.map { preferences ->
        AppSettings(
            darkMode = preferences[DARK_MODE] ?: false,
            autoRefresh = preferences[AUTO_REFRESH] ?: true,
            autoRefreshInterval = preferences[AUTO_REFRESH_INTERVAL] ?: 30,
            defaultBBox = preferences[DEFAULT_BBOX] ?: Constants.ITALY_BBOX,
            defaultAreaName = preferences[DEFAULT_AREA_NAME] ?: Constants.DEFAULT_AREA_NAME,
            showNotifications = preferences[SHOW_NOTIFICATIONS] ?: true,
            minChangesetsFilter = preferences[MIN_CHANGESETS_FILTER] ?: 0,
            cacheEnabled = preferences[CACHE_ENABLED] ?: true,
            osmchaToken = secureTokenStorage.getOsmchaToken(), // Read from secure storage
            osmchaChangesetsLimit = preferences[OSMCHA_CHANGESETS_LIMIT] ?: 100,
            lastKnownChangesetId = preferences[LAST_KNOWN_CHANGESET_ID] ?: 0L,
            monitoringAreas = decodeMonitoringAreas(preferences[MONITORING_AREAS_JSON]),
            debugLogsEnabled = preferences[DEBUG_LOGS_ENABLED] ?: false
        )
    }

    private fun decodeMonitoringAreas(serialized: String?): List<MonitoringArea> {
        if (serialized.isNullOrBlank()) return defaultMonitoringAreas()
        return try {
            val type = object : TypeToken<List<MonitoringArea>>() {}.type
            val parsed: List<MonitoringArea>? = gson.fromJson(serialized, type)
            parsed.orEmpty().ifEmpty { defaultMonitoringAreas() }
        } catch (e: Exception) {
            Timber.w(e, "Invalid monitoring areas payload, using defaults")
            defaultMonitoringAreas()
        }
    }

    private fun encodeMonitoringAreas(areas: List<MonitoringArea>): String {
        return gson.toJson(areas)
    }

    suspend fun updateDarkMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[DARK_MODE] = enabled
        }
    }

    suspend fun updateAutoRefresh(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AUTO_REFRESH] = enabled
        }
    }

    suspend fun updateAutoRefreshInterval(minutes: Int) {
        dataStore.edit { preferences ->
            preferences[AUTO_REFRESH_INTERVAL] = minutes
        }
    }

    suspend fun addMonitoringArea(area: MonitoringArea) {
        dataStore.edit { preferences ->
            val currentAreas = decodeMonitoringAreas(preferences[MONITORING_AREAS_JSON])
            if (currentAreas.none { it.bbox == area.bbox }) {
                preferences[MONITORING_AREAS_JSON] = encodeMonitoringAreas(currentAreas + area)
            }
        }
    }

    suspend fun removeMonitoringArea(bbox: String) {
        dataStore.edit { preferences ->
            val currentAreas = decodeMonitoringAreas(preferences[MONITORING_AREAS_JSON])
            val updatedAreas = currentAreas.filterNot { it.bbox == bbox }
            val safeAreas = if (updatedAreas.isEmpty()) defaultMonitoringAreas() else updatedAreas

            preferences[MONITORING_AREAS_JSON] = encodeMonitoringAreas(safeAreas)

            if (preferences[DEFAULT_BBOX] == bbox) {
                val fallback = safeAreas.first()
                preferences[DEFAULT_BBOX] = fallback.bbox
                preferences[DEFAULT_AREA_NAME] = fallback.name
            }
        }
    }

    suspend fun setDefaultMonitoringArea(area: MonitoringArea) {
        dataStore.edit { preferences ->
            val currentAreas = decodeMonitoringAreas(preferences[MONITORING_AREAS_JSON])
            if (currentAreas.none { it.bbox == area.bbox }) {
                preferences[MONITORING_AREAS_JSON] = encodeMonitoringAreas(currentAreas + area)
            }
            preferences[DEFAULT_BBOX] = area.bbox
            preferences[DEFAULT_AREA_NAME] = area.name
        }
    }

    suspend fun updateShowNotifications(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_NOTIFICATIONS] = enabled
        }
    }

    suspend fun updateMinChangesetsFilter(min: Int) {
        dataStore.edit { preferences ->
            preferences[MIN_CHANGESETS_FILTER] = min
        }
    }

    suspend fun updateCacheEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[CACHE_ENABLED] = enabled
        }
    }


    suspend fun updateOsmchaToken(token: String) {
        secureTokenStorage.saveOsmchaToken(normalizeOsmchaToken(token))
        dataStore.edit { preferences ->
            preferences.remove(OSMCHA_TOKEN)
        }
    }

    fun getOsmchaTokenOnce(): String = secureTokenStorage.getOsmchaToken()

    suspend fun updateOsmchaChangesetsLimit(limit: Int) {
        dataStore.edit { preferences ->
            preferences[OSMCHA_CHANGESETS_LIMIT] = limit
        }
    }

    suspend fun updateLastKnownChangesetId(id: Long) {
        dataStore.edit { preferences ->
            preferences[LAST_KNOWN_CHANGESET_ID] = id
        }
    }

    suspend fun updateDebugLogsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[DEBUG_LOGS_ENABLED] = enabled
        }
    }

    suspend fun resetToDefaults() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
        // Also clear secure token storage
        secureTokenStorage.clearOsmchaToken()
    }
}

internal fun normalizeOsmchaToken(rawToken: String): String {
    val trimmed = rawToken.trim()
    val withoutPrefix = if (trimmed.startsWith("Token ", ignoreCase = true)) {
        trimmed.substringAfter(' ', missingDelimiterValue = trimmed).trimStart()
    } else {
        trimmed
    }
    return withoutPrefix.filterNot { it.isWhitespace() }
}
