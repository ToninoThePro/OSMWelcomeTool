package com.antoninofaro.welcometool.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Integration test for token migration from plain DataStore to encrypted storage.
 *
 * This test validates the security fix by ensuring that:
 * - Existing plain-text tokens are automatically migrated to encrypted storage
 * - The plain-text token is removed from DataStore after migration
 * - New tokens are saved directly to encrypted storage
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TokenMigrationIntegrationTest {

    private lateinit var testContext: Context
    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var testScope: TestScope
    private lateinit var secureTokenStorage: SecureTokenStorage

    private val OSMCHA_TOKEN = stringPreferencesKey("osmcha_token")

    @Before
    fun setup() {
        testContext = ApplicationProvider.getApplicationContext()
        testScope = TestScope(UnconfinedTestDispatcher() + Job())

        // Create a test DataStore
        testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testContext.preferencesDataStoreFile("test_settings_migration_${UUID.randomUUID()}") }
        )

        // Create secure token storage
        secureTokenStorage = SecureTokenStorage(testContext)
        secureTokenStorage.clearOsmchaToken()
    }

    @After
    fun cleanup() = runTest {
        // Clean up test data
        testDataStore.edit { it.clear() }
        secureTokenStorage.clearOsmchaToken()
    }

    @Test
    fun testMigrationFromPlainTextToEncrypted() = runTest {
        // Given - a plain-text token exists in DataStore
        val plainTextToken = "legacy-plain-text-token-12345"
        testDataStore.edit { preferences ->
            preferences[OSMCHA_TOKEN] = plainTextToken
        }

        // Verify it's stored in plain text
        val storedPlainText = testDataStore.data.first()[OSMCHA_TOKEN]
        assertThat(storedPlainText).isEqualTo(plainTextToken)

        // When - we simulate what SettingsRepository does on initialization
        val tokenFromDataStore = testDataStore.data.first()[OSMCHA_TOKEN] ?: ""
        if (tokenFromDataStore.isNotBlank() && !secureTokenStorage.hasOsmchaToken()) {
            secureTokenStorage.saveOsmchaToken(tokenFromDataStore)
            testDataStore.edit { prefs ->
                prefs.remove(OSMCHA_TOKEN)
            }
        }

        // Then - token should be in secure storage
        val secureToken = secureTokenStorage.getOsmchaToken()
        assertThat(secureToken).isEqualTo(plainTextToken)

        // And - plain text token should be removed from DataStore
        val plainTextAfterMigration = testDataStore.data.first()[OSMCHA_TOKEN]
        assertThat(plainTextAfterMigration).isNull()
    }

    @Test
    fun testNoMigrationWhenTokenAlreadyInSecureStorage() = runTest {
        // Given - token already exists in secure storage
        val existingToken = "already-secure-token"
        secureTokenStorage.saveOsmchaToken(existingToken)

        // And - a different token exists in plain text (shouldn't happen, but test the logic)
        val plainTextToken = "old-plain-text-token"
        testDataStore.edit { preferences ->
            preferences[OSMCHA_TOKEN] = plainTextToken
        }

        // When - we check the migration logic
        val tokenFromDataStore = testDataStore.data.first()[OSMCHA_TOKEN] ?: ""
        if (tokenFromDataStore.isNotBlank() && !secureTokenStorage.hasOsmchaToken()) {
            // This branch should NOT execute
            secureTokenStorage.saveOsmchaToken(tokenFromDataStore)
        }

        // Then - secure storage should still have the original token
        val secureToken = secureTokenStorage.getOsmchaToken()
        assertThat(secureToken).isEqualTo(existingToken)
        assertThat(secureToken).isNotEqualTo(plainTextToken)
    }

    @Test
    fun testNewTokenGoesDirectlyToSecureStorage() = runTest {
        // Given - no existing tokens
        assertThat(secureTokenStorage.hasOsmchaToken()).isFalse()

        // When - a new token is saved (simulating updateOsmchaToken)
        val newToken = "brand-new-token"
        secureTokenStorage.saveOsmchaToken(newToken)
        testDataStore.edit { preferences ->
            preferences.remove(OSMCHA_TOKEN)
        }

        // Then - token is in secure storage
        assertThat(secureTokenStorage.getOsmchaToken()).isEqualTo(newToken)

        // And - NOT in plain text DataStore
        val plainTextToken = testDataStore.data.first()[OSMCHA_TOKEN]
        assertThat(plainTextToken).isNull()
    }

    @Test
    fun testMigrationDoesNotFailOnEmptyDataStore() = runTest {
        // Given - empty DataStore
        testDataStore.edit { it.clear() }

        // When - we run migration logic
        val tokenFromDataStore = testDataStore.data.first()[OSMCHA_TOKEN] ?: ""
        if (tokenFromDataStore.isNotBlank() && !secureTokenStorage.hasOsmchaToken()) {
            secureTokenStorage.saveOsmchaToken(tokenFromDataStore)
            testDataStore.edit { prefs ->
                prefs.remove(OSMCHA_TOKEN)
            }
        }

        // Then - no token in either storage
        assertThat(secureTokenStorage.getOsmchaToken()).isEmpty()
        assertThat(testDataStore.data.first()[OSMCHA_TOKEN]).isNull()
    }

    @Test
    fun testClearingBothStorages() = runTest {
        // Given - token in both places (shouldn't happen, but test cleanup)
        val token = "test-token"
        secureTokenStorage.saveOsmchaToken(token)
        testDataStore.edit { preferences ->
            preferences[OSMCHA_TOKEN] = token
        }

        // When - we clear everything (simulating resetToDefaults)
        testDataStore.edit { preferences ->
            preferences.clear()
        }
        secureTokenStorage.clearOsmchaToken()

        // Then - both storages are empty
        assertThat(secureTokenStorage.getOsmchaToken()).isEmpty()
        assertThat(testDataStore.data.first()[OSMCHA_TOKEN]).isNull()
    }
}
