package com.antoninofaro.welcometool.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Security tests for SecureTokenStorage
 *
 * Validates that the secure token storage:
 * - Properly encrypts and decrypts tokens
 * - Handles empty/null values correctly
 * - Persists data across instances
 * - Clears data properly
 */
@RunWith(RobolectricTestRunner::class)
class SecureTokenStorageTest {

    private lateinit var context: Context
    private lateinit var storage: SecureTokenStorage

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        storage = SecureTokenStorage(context)
        // Clean slate for each test
        storage.clearOsmchaToken()
    }

    @After
    fun cleanup() {
        storage.clearOsmchaToken()
    }

    @Test
    fun testSaveAndRetrieveToken() {
        // Given
        val testToken = "test-osmcha-token-12345"

        // When
        storage.saveOsmchaToken(testToken)
        val retrieved = storage.getOsmchaToken()

        // Then
        assertThat(retrieved).isEqualTo(testToken)
    }

    @Test
    fun testRetrieveEmptyTokenByDefault() {
        // When - no token has been saved
        val retrieved = storage.getOsmchaToken()

        // Then
        assertThat(retrieved).isEmpty()
    }

    @Test
    fun testClearToken() {
        // Given
        val testToken = "token-to-clear"
        storage.saveOsmchaToken(testToken)

        // When
        storage.clearOsmchaToken()
        val retrieved = storage.getOsmchaToken()

        // Then
        assertThat(retrieved).isEmpty()
    }

    @Test
    fun testHasToken() {
        // Initially no token
        assertThat(storage.hasOsmchaToken()).isFalse()

        // After saving
        storage.saveOsmchaToken("some-token")
        assertThat(storage.hasOsmchaToken()).isTrue()

        // After clearing
        storage.clearOsmchaToken()
        assertThat(storage.hasOsmchaToken()).isFalse()
    }

    @Test
    fun testUpdateToken() {
        // Given
        val firstToken = "first-token"
        val secondToken = "second-token"

        // When
        storage.saveOsmchaToken(firstToken)
        val retrievedFirst = storage.getOsmchaToken()

        storage.saveOsmchaToken(secondToken)
        val retrievedSecond = storage.getOsmchaToken()

        // Then
        assertThat(retrievedFirst).isEqualTo(firstToken)
        assertThat(retrievedSecond).isEqualTo(secondToken)
        assertThat(retrievedSecond).isNotEqualTo(retrievedFirst)
    }

    @Test
    fun testPersistenceAcrossInstances() {
        // Given
        val testToken = "persistent-token"
        storage.saveOsmchaToken(testToken)

        // When - create a new instance
        val newStorage = SecureTokenStorage(context)
        val retrieved = newStorage.getOsmchaToken()

        // Then
        assertThat(retrieved).isEqualTo(testToken)

        // Cleanup
        newStorage.clearOsmchaToken()
    }

    @Test
    fun testEmptyStringToken() {
        // Given
        storage.saveOsmchaToken("")

        // When
        val retrieved = storage.getOsmchaToken()
        val hasToken = storage.hasOsmchaToken()

        // Then
        assertThat(retrieved).isEmpty()
        assertThat(hasToken).isFalse()
    }

    @Test
    fun testLongToken() {
        // Given - simulate a very long token
        val longToken = "a".repeat(1000)

        // When
        storage.saveOsmchaToken(longToken)
        val retrieved = storage.getOsmchaToken()

        // Then
        assertThat(retrieved).isEqualTo(longToken)
        assertThat(retrieved).hasLength(1000)
    }

    @Test
    fun testSpecialCharactersInToken() {
        // Given
        val specialToken = "token-with-special-chars!@#$%^&*()_+-=[]{}|;:',.<>?/~`"

        // When
        storage.saveOsmchaToken(specialToken)
        val retrieved = storage.getOsmchaToken()

        // Then
        assertThat(retrieved).isEqualTo(specialToken)
    }

    @Test
    fun testTokenWithWhitespace() {
        // Given
        val tokenWithSpaces = "token with spaces and\ttabs\nnewlines"

        // When
        storage.saveOsmchaToken(tokenWithSpaces)
        val retrieved = storage.getOsmchaToken()

        // Then
        assertThat(retrieved).isEqualTo(tokenWithSpaces)
    }
}

