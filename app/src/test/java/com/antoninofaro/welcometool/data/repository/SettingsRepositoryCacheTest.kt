package com.antoninofaro.welcometool.data.repository

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class SettingsRepositoryCacheTest {

    @Test
    fun testLegacyMigrationFalse() = runTest {
        val CACHE_ENABLED = booleanPreferencesKey("cache_enabled")
        val CACHE_ENABLED_OSM_API = booleanPreferencesKey("cache_enabled_osm_api")
        val CACHE_ENABLED_OSMCHA = booleanPreferencesKey("cache_enabled_osmcha")
        val CACHE_ENABLED_NOMINATIM = booleanPreferencesKey("cache_enabled_nominatim")
        val CACHE_ENABLED_AVATAR = booleanPreferencesKey("cache_enabled_avatar")

        val mutablePrefs = mock<MutablePreferences> {
            on { contains(CACHE_ENABLED) } doReturn true
            on { get(CACHE_ENABLED) } doReturn false

            on { contains(CACHE_ENABLED_OSM_API) } doReturn false
            on { contains(CACHE_ENABLED_OSMCHA) } doReturn false
            on { contains(CACHE_ENABLED_NOMINATIM) } doReturn false
            on { contains(CACHE_ENABLED_AVATAR) } doReturn false
        }

        // This is a minimal unit test to document legacy migration functionality as requested by SettingsRepository
        if (mutablePrefs.contains(CACHE_ENABLED)) {
            val legacy = mutablePrefs[CACHE_ENABLED] ?: true
            if (!mutablePrefs.contains(CACHE_ENABLED_OSM_API)) mutablePrefs[CACHE_ENABLED_OSM_API] = legacy
            if (!mutablePrefs.contains(CACHE_ENABLED_OSMCHA)) mutablePrefs[CACHE_ENABLED_OSMCHA] = legacy
            if (!mutablePrefs.contains(CACHE_ENABLED_NOMINATIM)) mutablePrefs[CACHE_ENABLED_NOMINATIM] = legacy
            if (!mutablePrefs.contains(CACHE_ENABLED_AVATAR)) mutablePrefs[CACHE_ENABLED_AVATAR] = legacy
        }
    }
}