package com.antoninofaro.welcometool.data.repository

import android.content.Context
import android.content.SharedPreferences
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TokenMigrationIntegrationTest {

    private lateinit var ctx: Context
    private lateinit var ds: DataStore<Preferences>
    private lateinit var secure: SecureTokenStorage
    private lateinit var prefs: SharedPreferences
    private lateinit var scope: TestScope
    private val KEY = stringPreferencesKey("osmcha_token")

    @Before
    fun setup() {
        ctx = ApplicationProvider.getApplicationContext()
        scope = TestScope(UnconfinedTestDispatcher())
        ds = PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { ctx.preferencesDataStoreFile("test_mig_${UUID.randomUUID()}") }
        )
        prefs = ctx.getSharedPreferences("test_sec_${UUID.randomUUID()}", Context.MODE_PRIVATE)
        secure = SecureTokenStorage(ctx)
        secure.injectTestPrefs(prefs)
        secure.clearOsmchaToken()
    }

    @After
    fun cleanup() = runTest {
        ds.edit { it.clear() }
        prefs.edit().clear().apply()
    }

    @Test
    fun testMigrationFromPlainTextToEncrypted() = runTest {
        ds.edit { it[KEY] = "legacy-plain-text-token-12345" }
        assertThat(ds.data.first()[KEY]).isEqualTo("legacy-plain-text-token-12345")

        val token = ds.data.first()[KEY] ?: ""
        if (token.isNotBlank() && !secure.hasOsmchaToken()) {
            secure.saveOsmchaToken(token)
            ds.edit { it.remove(KEY) }
        }

        assertThat(secure.getOsmchaToken()).isEqualTo("legacy-plain-text-token-12345")
        assertThat(ds.data.first()[KEY]).isNull()
    }

    @Test
    fun testNoMigrationWhenTokenAlreadyInSecureStorage() = runTest {
        secure.saveOsmchaToken("already-secure-token")
        ds.edit { it[KEY] = "old-plain-text-token" }
        val token = ds.data.first()[KEY] ?: ""
        if (token.isNotBlank() && !secure.hasOsmchaToken()) secure.saveOsmchaToken(token)
        assertThat(secure.getOsmchaToken()).isEqualTo("already-secure-token")
        assertThat(secure.getOsmchaToken()).isNotEqualTo("old-plain-text-token")
    }

    @Test
    fun testNewTokenGoesDirectlyToSecureStorage() = runTest {
        assertThat(secure.hasOsmchaToken()).isFalse()
        secure.saveOsmchaToken("brand-new-token")
        ds.edit { it.remove(KEY) }
        assertThat(secure.getOsmchaToken()).isEqualTo("brand-new-token")
        assertThat(ds.data.first()[KEY]).isNull()
    }

    @Test
    fun testMigrationDoesNotFailOnEmptyDataStore() = runTest {
        ds.edit { it.clear() }
        val token = ds.data.first()[KEY] ?: ""
        if (token.isNotBlank() && !secure.hasOsmchaToken()) {
            secure.saveOsmchaToken(token)
            ds.edit { it.remove(KEY) }
        }
        assertThat(secure.getOsmchaToken()).isEmpty()
        assertThat(ds.data.first()[KEY]).isNull()
    }

    @Test
    fun testClearingBothStorages() = runTest {
        secure.saveOsmchaToken("test-token")
        ds.edit { it[KEY] = "test-token" }
        ds.edit { it.clear() }
        secure.clearOsmchaToken()
        assertThat(secure.getOsmchaToken()).isEmpty()
        assertThat(ds.data.first()[KEY]).isNull()
    }
}
