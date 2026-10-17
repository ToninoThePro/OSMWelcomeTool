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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val themeMode: String = "system",
    val dynamicColor: Boolean = false,
    val autoRefresh: Boolean = true,
    val autoRefreshInterval: Int = 30,
    val defaultBBox: String = Constants.WORLD_BBOX,
    val defaultAreaName: String = Constants.DEFAULT_AREA_NAME,
    val showNotifications: Boolean = false,
    val showNewChangesetNotifications: Boolean = false,
    val individualChangesetNotifications: Boolean = false,
    val notificationAutoDismissHours: Int = 0,
    val minChangesetsFilter: Int = 0,
    val cacheEnabledOsmApi: Boolean = true,
    val cacheEnabledOsmCha: Boolean = true,
    val cacheEnabledNominatim: Boolean = true,
    val cacheEnabledAvatar: Boolean = true,
    val osmchaToken: String = "",
    val verifiedOsmchaUsername: String = "",
    val osmchaChangesetsLimit: Int = 100,
    val lastKnownChangesetId: Long = 0L,
    val lastKnownChangesetDate: String = "",
    val lastSyncStartTimeMs: Long = 0L,
    val lastPhaseBTimeMs: Long = 0L,
    val isHistoricalExhausted: Boolean = false,
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
    MonitoringArea(Constants.DEFAULT_AREA_NAME, Constants.WORLD_BBOX)
)

@Singleton
class SettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val secureTokenStorage: SecureTokenStorage,
    private val json: Json
) {
    private val dataStore = context.dataStore

    private val _cacheFlags = kotlinx.coroutines.flow.MutableStateFlow(
        CacheFlags(
            osmApi = true,
            osmCha = true,
            nominatim = true,
            avatar = true
        )
    )
    internal val cacheFlagsFlow: kotlinx.coroutines.flow.StateFlow<CacheFlags> = _cacheFlags

    data class CacheFlags(
        val osmApi: Boolean,
        val osmCha: Boolean,
        val nominatim: Boolean,
        val avatar: Boolean
    )

    fun isOsmApiCacheEnabledOnce(): Boolean = _cacheFlags.value.osmApi
    fun isOsmChaCacheEnabledOnce(): Boolean = _cacheFlags.value.osmCha
    fun isNominatimCacheEnabledOnce(): Boolean = _cacheFlags.value.nominatim
    fun isAvatarCacheEnabledOnce(): Boolean = _cacheFlags.value.avatar

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
        private val INDIVIDUAL_CHANGESET_NOTIFICATIONS =
            booleanPreferencesKey("individual_changeset_notifications")
        private val NOTIFICATION_AUTO_DISMISS_HOURS =
            intPreferencesKey("notification_auto_dismiss_hours")
        private val MIN_CHANGESETS_FILTER = intPreferencesKey("min_changesets_filter")
        private val CACHE_ENABLED = booleanPreferencesKey("cache_enabled") // legacy
        private val CACHE_ENABLED_OSM_API = booleanPreferencesKey("cache_enabled_osm_api")
        private val CACHE_ENABLED_OSMCHA = booleanPreferencesKey("cache_enabled_osmcha")
        private val CACHE_ENABLED_NOMINATIM = booleanPreferencesKey("cache_enabled_nominatim")
        private val CACHE_ENABLED_AVATAR = booleanPreferencesKey("cache_enabled_avatar")

        private val VERIFIED_OSMCHA_USERNAME = stringPreferencesKey("verified_osmcha_username")

        private val OSMCHA_CHANGESETS_LIMIT = intPreferencesKey("osmcha_changesets_limit")
        private val LAST_KNOWN_CHANGESET_ID = longPreferencesKey("last_known_changeset_id")
        private val LAST_KNOWN_CHANGESET_DATE = stringPreferencesKey("last_known_changeset_date")
        private val LAST_SYNC_START_TIME_MS = longPreferencesKey("last_sync_start_time_ms")
        private val LAST_PHASE_B_TIME_MS = longPreferencesKey("last_phase_b_time_ms")
        private val IS_HISTORICAL_EXHAUSTED = booleanPreferencesKey("is_historical_exhausted")
        private val OSMCHA_AUTO_REFRESH_DAYS = intPreferencesKey("osmcha_auto_refresh_days")
        private val MONITORING_AREAS_JSON = stringPreferencesKey("monitoring_areas_json")
        private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val settingsFlow: Flow<AppSettings> = dataStore.data.map { preferences ->
        // Legacy migration
        val legacyCacheEnabled = preferences[CACHE_ENABLED]
        
        val cacheOsmApi = preferences[CACHE_ENABLED_OSM_API] ?: legacyCacheEnabled ?: true
        val cacheOsmCha = preferences[CACHE_ENABLED_OSMCHA] ?: legacyCacheEnabled ?: true
        val cacheNominatim = preferences[CACHE_ENABLED_NOMINATIM] ?: legacyCacheEnabled ?: true
        val cacheAvatar = preferences[CACHE_ENABLED_AVATAR] ?: legacyCacheEnabled ?: true

        _cacheFlags.value = CacheFlags(
            osmApi = cacheOsmApi,
            osmCha = cacheOsmCha,
            nominatim = cacheNominatim,
            avatar = cacheAvatar
        )

        val defaultBBox = preferences[DEFAULT_BBOX] ?: Constants.WORLD_BBOX
        val defaultAreaName = preferences[DEFAULT_AREA_NAME] ?: Constants.DEFAULT_AREA_NAME
        val monitoringAreas = getMonitoringAreas(preferences)

        AppSettings(
            themeMode = preferences[THEME_MODE] ?: "system",
            dynamicColor = preferences[DYNAMIC_COLOR] ?: false,
            autoRefresh = preferences[AUTO_REFRESH] ?: true,
            autoRefreshInterval = preferences[AUTO_REFRESH_INTERVAL] ?: 30,
            defaultBBox = defaultBBox,
            defaultAreaName = defaultAreaName,
            showNotifications = preferences[SHOW_NOTIFICATIONS] ?: false,
            showNewChangesetNotifications = preferences[SHOW_NEW_CHANGESET_NOTIFICATIONS] ?: false,
            individualChangesetNotifications = preferences[INDIVIDUAL_CHANGESET_NOTIFICATIONS] ?: false,
            notificationAutoDismissHours = preferences[NOTIFICATION_AUTO_DISMISS_HOURS] ?: 0,
            minChangesetsFilter = preferences[MIN_CHANGESETS_FILTER] ?: 0,
            cacheEnabledOsmApi = cacheOsmApi,
            cacheEnabledOsmCha = cacheOsmCha,
            cacheEnabledNominatim = cacheNominatim,
            cacheEnabledAvatar = cacheAvatar,
            osmchaToken = secureTokenStorage.getOsmchaToken(),
            verifiedOsmchaUsername = preferences[VERIFIED_OSMCHA_USERNAME] ?: "",
            osmchaChangesetsLimit = preferences[OSMCHA_CHANGESETS_LIMIT] ?: 100,
            lastKnownChangesetId = preferences[LAST_KNOWN_CHANGESET_ID] ?: 0L,
            lastKnownChangesetDate = preferences[LAST_KNOWN_CHANGESET_DATE] ?: "",
            lastSyncStartTimeMs = preferences[LAST_SYNC_START_TIME_MS] ?: 0L,
            lastPhaseBTimeMs = preferences[LAST_PHASE_B_TIME_MS] ?: 0L,
            isHistoricalExhausted = preferences[IS_HISTORICAL_EXHAUSTED] ?: false,
            osmchaAutoRefreshDays = preferences[OSMCHA_AUTO_REFRESH_DAYS] ?: 1,
            monitoringAreas = monitoringAreas,
            isOnboardingCompleted = preferences[ONBOARDING_COMPLETED] ?: false,
            isLoaded = true,
        )
    }

    private fun getMonitoringAreas(preferences: Preferences): List<MonitoringArea> {
        val decoded = decodeMonitoringAreas(preferences[MONITORING_AREAS_JSON])
        if (decoded.isNotEmpty()) {
            return decoded
        }

        val defaultBBox = preferences[DEFAULT_BBOX] ?: Constants.WORLD_BBOX
        val defaultAreaName = preferences[DEFAULT_AREA_NAME] ?: Constants.DEFAULT_AREA_NAME
        return listOf(MonitoringArea(defaultAreaName, defaultBBox))
    }

    private fun decodeMonitoringAreas(serialized: String?): List<MonitoringArea> {
        if (serialized.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<MonitoringArea>>(serialized)
        } catch (e: Exception) {
            Timber.w(e, "Invalid monitoring areas payload")
            emptyList()
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
            val currentAreas = getMonitoringAreas(preferences)
            if (currentAreas.none { it.bbox == area.bbox }) {
                preferences[MONITORING_AREAS_JSON] = encodeMonitoringAreas(currentAreas + area)
            }
        }
    }

    suspend fun removeMonitoringArea(bbox: String) {
        dataStore.edit { preferences ->
            val currentAreas = getMonitoringAreas(preferences)
            val updatedAreas = currentAreas.filterNot { it.bbox == bbox }
            if (updatedAreas.isNotEmpty()) {
                preferences[MONITORING_AREAS_JSON] = encodeMonitoringAreas(updatedAreas)

                if (preferences[DEFAULT_BBOX] == bbox) {
                    val fallback = updatedAreas.first()
                    preferences[DEFAULT_BBOX] = fallback.bbox
                    preferences[DEFAULT_AREA_NAME] = fallback.name
                }
            }
        }
    }

    suspend fun setDefaultMonitoringArea(area: MonitoringArea) {
        dataStore.edit { preferences ->
            val currentAreas = getMonitoringAreas(preferences)
            val updatedAreas = if (currentAreas.none { it.bbox == area.bbox }) {
                currentAreas + area
            } else {
                currentAreas
            }
            preferences[MONITORING_AREAS_JSON] = encodeMonitoringAreas(updatedAreas)
            preferences[DEFAULT_BBOX] = area.bbox
            preferences[DEFAULT_AREA_NAME] = area.name
        }
    }

    suspend fun updateShowNotifications(enabled: Boolean) =
        updatePreference(SHOW_NOTIFICATIONS, enabled)

    suspend fun updateShowNewChangesetNotifications(enabled: Boolean) =
        updatePreference(SHOW_NEW_CHANGESET_NOTIFICATIONS, enabled)

    suspend fun updateIndividualChangesetNotifications(enabled: Boolean) =
        updatePreference(INDIVIDUAL_CHANGESET_NOTIFICATIONS, enabled)

    suspend fun updateNotificationAutoDismissHours(hours: Int) =
        updatePreference(NOTIFICATION_AUTO_DISMISS_HOURS, hours)

    suspend fun updateMinChangesetsFilter(min: Int) = updatePreference(MIN_CHANGESETS_FILTER, min)

    suspend fun updateCacheCategoryEnabled(categoryId: String, enabled: Boolean) {
        dataStore.edit { preferences ->
            // Materialize legacy flag into new flags before deleting it
            if (preferences.contains(CACHE_ENABLED)) {
                val legacy = preferences[CACHE_ENABLED] ?: true
                if (!preferences.contains(CACHE_ENABLED_OSM_API)) preferences[CACHE_ENABLED_OSM_API] = legacy
                if (!preferences.contains(CACHE_ENABLED_OSMCHA)) preferences[CACHE_ENABLED_OSMCHA] = legacy
                if (!preferences.contains(CACHE_ENABLED_NOMINATIM)) preferences[CACHE_ENABLED_NOMINATIM] = legacy
                if (!preferences.contains(CACHE_ENABLED_AVATAR)) preferences[CACHE_ENABLED_AVATAR] = legacy
                preferences.remove(CACHE_ENABLED)
            }

            when (categoryId) {
                "osm_api" -> preferences[CACHE_ENABLED_OSM_API] = enabled
                "osmcha" -> preferences[CACHE_ENABLED_OSMCHA] = enabled
                "nominatim" -> preferences[CACHE_ENABLED_NOMINATIM] = enabled
                "avatar" -> preferences[CACHE_ENABLED_AVATAR] = enabled
            }
        }
        // Update cache flags from DataStore
        val prefs = dataStore.data.first()
        val legacyCacheEnabled = prefs[CACHE_ENABLED]
        _cacheFlags.value = CacheFlags(
            osmApi = prefs[CACHE_ENABLED_OSM_API] ?: legacyCacheEnabled ?: true,
            osmCha = prefs[CACHE_ENABLED_OSMCHA] ?: legacyCacheEnabled ?: true,
            nominatim = prefs[CACHE_ENABLED_NOMINATIM] ?: legacyCacheEnabled ?: true,
            avatar = prefs[CACHE_ENABLED_AVATAR] ?: legacyCacheEnabled ?: true
        )
    }

    // Keep this for backwards compatibility if still called somewhere, mapping to all
    suspend fun updateCacheEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[CACHE_ENABLED_OSM_API] = enabled
            preferences[CACHE_ENABLED_OSMCHA] = enabled
            preferences[CACHE_ENABLED_NOMINATIM] = enabled
            preferences[CACHE_ENABLED_AVATAR] = enabled
            preferences.remove(CACHE_ENABLED)
        }
        _cacheFlags.value = CacheFlags(
            osmApi = enabled,
            osmCha = enabled,
            nominatim = enabled,
            avatar = enabled
        )
    }

    fun updateOsmchaToken(token: String) {
        val normalized = normalizeOsmchaToken(token)
        secureTokenStorage.saveOsmchaToken(normalized)
    }

    fun getOsmchaTokenOnce(): String = secureTokenStorage.getOsmchaToken()

    suspend fun updateVerifiedOsmchaUsername(username: String) =
        updatePreference(VERIFIED_OSMCHA_USERNAME, username)


    suspend fun clearOsmchaToken() {
        secureTokenStorage.clearOsmchaToken()
        updatePreference(VERIFIED_OSMCHA_USERNAME, "")
    }

    suspend fun updateOsmchaChangesetsLimit(limit: Int) =
        updatePreference(OSMCHA_CHANGESETS_LIMIT, limit)

    suspend fun updateLastKnownChangesetId(id: Long) = updatePreference(LAST_KNOWN_CHANGESET_ID, id)

    suspend fun updateLastKnownChangesetDate(date: String) =
        updatePreference(LAST_KNOWN_CHANGESET_DATE, date)

    suspend fun updateLastSyncStartTimeMs(timestampMs: Long) =
        updatePreference(LAST_SYNC_START_TIME_MS, timestampMs)

    suspend fun updateLastPhaseBTimeMs(timestampMs: Long) =
        updatePreference(LAST_PHASE_B_TIME_MS, timestampMs)

    suspend fun updateIsHistoricalExhausted(exhausted: Boolean) =
        updatePreference(IS_HISTORICAL_EXHAUSTED, exhausted)

    suspend fun updateOsmchaAutoRefreshDays(days: Int) =
        updatePreference(OSMCHA_AUTO_REFRESH_DAYS, days)

    suspend fun updateOnboardingCompleted(completed: Boolean) =
        updatePreference(ONBOARDING_COMPLETED, completed)

    suspend fun completeOnboarding(area: MonitoringArea) {
        dataStore.edit { preferences ->
            preferences[MONITORING_AREAS_JSON] = encodeMonitoringAreas(listOf(area))
            preferences[DEFAULT_BBOX] = area.bbox
            preferences[DEFAULT_AREA_NAME] = area.name
            preferences[ONBOARDING_COMPLETED] = true
        }
    }

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
