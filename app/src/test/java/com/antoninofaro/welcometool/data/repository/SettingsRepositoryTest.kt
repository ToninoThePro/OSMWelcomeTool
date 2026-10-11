package com.antoninofaro.welcometool.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import java.io.File

// Access the datastore for setup using the same extension logic or reflection, 
// actually since it's private in the file, we can just use the repository to clear it.
// Or we can define a matching extension here since it's based on name "settings".
private val Context.testDataStore by preferencesDataStore(name = "settings")

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class SettingsRepositoryTest {

    private lateinit var context: Context
    private lateinit var secureTokenStorage: SecureTokenStorage
    private lateinit var repository: SettingsRepository

    private val CACHE_ENABLED = booleanPreferencesKey("cache_enabled")
    private val CACHE_ENABLED_OSM_API = booleanPreferencesKey("cache_enabled_osm_api")

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        secureTokenStorage = mock()
        whenever(secureTokenStorage.getOsmchaToken()).thenReturn("")
        
        repository = SettingsRepository(context, secureTokenStorage, Json)
    }

    @After
    fun teardown() = runTest {
        repository.resetToDefaults()
    }

    @Test
    fun testLegacyCacheEnabledMigration_false() = runTest {
        // Setup legacy state
        context.testDataStore.edit { it[CACHE_ENABLED] = false }
        
        // When we read flow, it should migrate false to all new flags
        val settings = repository.settingsFlow.first()
        
        assertThat(settings.cacheEnabledOsmApi).isFalse()
        assertThat(settings.cacheEnabledOsmCha).isFalse()
        assertThat(settings.cacheEnabledNominatim).isFalse()
        assertThat(settings.cacheEnabledAvatar).isFalse()
    }

    @Test
    fun testLegacyCacheEnabledMigration_true() = runTest {
        // Setup legacy state
        context.testDataStore.edit { it[CACHE_ENABLED] = true }
        
        val settings = repository.settingsFlow.first()
        
        assertThat(settings.cacheEnabledOsmApi).isTrue()
        assertThat(settings.cacheEnabledOsmCha).isTrue()
        assertThat(settings.cacheEnabledNominatim).isTrue()
        assertThat(settings.cacheEnabledAvatar).isTrue()
    }

    @Test
    fun testCategoryToggle_materializesLegacyStateAndDeletesLegacyKey() = runTest {
        // Start with legacy true
        context.testDataStore.edit { it[CACHE_ENABLED] = true }

        // Change one flag
        repository.updateCacheCategoryEnabled("osm_api", false)

        val settings = repository.settingsFlow.first()
        
        // Only osm_api should be false
        assertThat(settings.cacheEnabledOsmApi).isFalse()
        
        // Others should retain the legacy true state because it was materialized
        assertThat(settings.cacheEnabledOsmCha).isTrue()
        assertThat(settings.cacheEnabledNominatim).isTrue()
        assertThat(settings.cacheEnabledAvatar).isTrue()

        // Ensure legacy key is removed
        val prefs = context.testDataStore.data.first()
        assertThat(prefs.contains(CACHE_ENABLED)).isFalse()
        
        // Verify materialized keys exist
        assertThat(prefs[CACHE_ENABLED_OSM_API]).isFalse()
        assertThat(prefs[booleanPreferencesKey("cache_enabled_osmcha")]).isTrue()
    }
}
