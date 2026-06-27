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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val themeMode: String = "system",
    val dynamicColor: Boolean = false,
    val autoRefresh: Boolean = true,
    val autoRefreshInterval: Int = 30,
    val defaultBBox: String = Constants.ITALY_BBOX,
    val defaultAreaName: String = Constants.DEFAULT_AREA_NAME,
    val showNotifications: Boolean = false,
    val showNewChangesetNotifications: Boolean = false,
    val minChangesetsFilter: Int = 0,
    val cacheEnabled: Boolean = true,
    val osmchaToken: String = "",
    val verifiedOsmchaUsername: String = "",
    val osmchaChangesetsLimit: Int = 100,
    val lastKnownChangesetId: Long = 0L,
    val lastKnownChangesetDate: String = "",
    val osmchaAutoRefreshDays: Int = 1,
    val monitoringAreas: List<MonitoringArea> = defaultMonitoringAreas(),
    val isOnboardingCompleted: Boolean = false,
    val isLoaded: Boolean = false,
)

@Serializable
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
    private val secureTokenStorage: SecureTokenStorage,
    private val json: Json
) {
    private val dataStore = context.dataStore
    private var cachedOsmchaToken: String = secureTokenStorage.getOsmchaToken()

    companion object {
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val AUTO_REFRESH = booleanPreferencesKey("auto_refresh")
        private val AUTO_REFRESH_INTERVAL = intPreferencesKey("auto_refresh_interval")
        private val DEFAULT_BBOX = stringPreferencesKey("default_bbox")
        private val DEFAULT_AREA_NAME = stringPreferencesKey("default_area_name")
        private val SHOW_NOTIFICATIONS = booleanPreferencesKey("show_notifications")
        private val SHOW_NEW_CHANGESET_NOTIFICATIONS =
            booleanPreferencesKey("show_new_changeset_notifications")
        private val MIN_CHANGESETS_FILTER = intPreferencesKey("min_changesets_filter")
        private val CACHE_ENABLED = booleanPreferencesKey("cache_enabled")

        private val VERIFIED_OSMCHA_USERNAME = stringPreferencesKey("verified_osmcha_username")

        private val OSMCHA_CHANGESETS_LIMIT = intPreferencesKey("osmcha_changesets_limit")
        private val LAST_KNOWN_CHANGESET_ID = longPreferencesKey("last_known_changeset_id")
        private val LAST_KNOWN_CHANGESET_DATE = stringPreferencesKey("last_known_changeset_date")
        private val OSMCHA_AUTO_REFRESH_DAYS = intPreferencesKey("osmcha_auto_refresh_days")
        private val MONITORING_AREAS_JSON = stringPreferencesKey("monitoring_areas_json")
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val settingsFlow: Flow<AppSettings> = dataStore.data.map { preferences ->
        AppSettings(
            themeMode = preferences[THEME_MODE] ?: "system",
            dynamicColor = preferences[DYNAMIC_COLOR] ?: false,
            autoRefresh = preferences[AUTO_REFRESH] ?: true,
            autoRefreshInterval = preferences[AUTO_REFRESH_INTERVAL] ?: 30,
            defaultBBox = preferences[DEFAULT_BBOX] ?: Constants.ITALY_BBOX,
            defaultAreaName = preferences[DEFAULT_AREA_NAME] ?: Constants.DEFAULT_AREA_NAME,
            showNotifications = preferences[SHOW_NOTIFICATIONS] ?: false,
            showNewChangesetNotifications = preferences[SHOW_NEW_CHANGESET_NOTIFICATIONS] ?: false,
            minChangesetsFilter = preferences[MIN_CHANGESETS_FILTER] ?: 0,
            cacheEnabled = preferences[CACHE_ENABLED] ?: true,
            osmchaToken = cachedOsmchaToken,
            verifiedOsmchaUsername = preferences[VERIFIED_OSMCHA_USERNAME] ?: "",
            osmchaChangesetsLimit = preferences[OSMCHA_CHANGESETS_LIMIT] ?: 100,
            lastKnownChangesetId = preferences[LAST_KNOWN_CHANGESET_ID] ?: 0L,
            lastKnownChangesetDate = preferences[LAST_KNOWN_CHANGESET_DATE] ?: "",
            osmchaAutoRefreshDays = preferences[OSMCHA_AUTO_REFRESH_DAYS] ?: 1,
            monitoringAreas = decodeMonitoringAreas(preferences[MONITORING_AREAS_JSON]),
            isOnboardingCompleted = preferences[ONBOARDING_COMPLETED] ?: false,
            isLoaded = true,
        )
    }

    private fun decodeMonitoringAreas(serialized: String?): List<MonitoringArea> {
        if (serialized.isNullOrBlank()) return defaultMonitoringAreas()
        return try {
            json.decodeFromString<List<MonitoringArea>>(serialized)
                .ifEmpty { defaultMonitoringAreas() }
        } catch (e: Exception) {
            Timber.w(e, "Invalid monitoring areas payload, using defaults")
            defaultMonitoringAreas()
        }
    }

    private fun encodeMonitoringAreas(areas: List<MonitoringArea>): String {
        return json.encodeToString(areas)
    }

    private suspend fun <T> updatePreference(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    suspend fun updateThemeMode(mode: String) = updatePreference(THEME_MODE, mode)

    suspend fun updateDynamicColor(enabled: Boolean) = updatePreference(DYNAMIC_COLOR, enabled)

    suspend fun updateAutoRefresh(enabled: Boolean) = updatePreference(AUTO_REFRESH, enabled)

    suspend fun updateAutoRefreshInterval(minutes: Int) =
        updatePreference(AUTO_REFRESH_INTERVAL, minutes)

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

    suspend fun updateShowNotifications(enabled: Boolean) =
        updatePreference(SHOW_NOTIFICATIONS, enabled)

    suspend fun updateShowNewChangesetNotifications(enabled: Boolean) =
        updatePreference(SHOW_NEW_CHANGESET_NOTIFICATIONS, enabled)

    suspend fun updateMinChangesetsFilter(min: Int) = updatePreference(MIN_CHANGESETS_FILTER, min)

    suspend fun updateCacheEnabled(enabled: Boolean) = updatePreference(CACHE_ENABLED, enabled)

    suspend fun updateOsmchaToken(token: String) {
        val normalized = normalizeOsmchaToken(token)
        secureTokenStorage.saveOsmchaToken(normalized)
        cachedOsmchaToken = normalized
    }

    fun getOsmchaTokenOnce(): String = secureTokenStorage.getOsmchaToken()

    suspend fun updateVerifiedOsmchaUsername(username: String) =
        updatePreference(VERIFIED_OSMCHA_USERNAME, username)

    suspend fun clearVerifiedOsmchaUsername() = updatePreference(VERIFIED_OSMCHA_USERNAME, "")

    suspend fun clearOsmchaToken() {
        secureTokenStorage.clearOsmchaToken()
        cachedOsmchaToken = ""
        updatePreference(VERIFIED_OSMCHA_USERNAME, "")
    }

    suspend fun updateOsmchaChangesetsLimit(limit: Int) =
        updatePreference(OSMCHA_CHANGESETS_LIMIT, limit)

    suspend fun updateLastKnownChangesetId(id: Long) = updatePreference(LAST_KNOWN_CHANGESET_ID, id)

    suspend fun updateLastKnownChangesetDate(date: String) =
        updatePreference(LAST_KNOWN_CHANGESET_DATE, date)

    suspend fun updateOsmchaAutoRefreshDays(days: Int) =
        updatePreference(OSMCHA_AUTO_REFRESH_DAYS, days)

    suspend fun updateOnboardingCompleted(completed: Boolean) =
        updatePreference(ONBOARDING_COMPLETED, completed)

    suspend fun resetToDefaults() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
        clearOsmchaToken()
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
