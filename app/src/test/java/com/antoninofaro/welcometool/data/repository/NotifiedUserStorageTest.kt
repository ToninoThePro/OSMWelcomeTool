package com.antoninofaro.welcometool.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.system.measureTimeMillis
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Test per NotifiedUserStorage
 * Verifica che il sistema di tracciamento degli utenti notificati funzioni correttamente
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NotifiedUserStorageTest {

    private lateinit var testContext: Context
    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var testScope: TestScope
    private lateinit var storage: NotifiedUserStorage

    @Before
    fun setup() {
        testContext = ApplicationProvider.getApplicationContext()
        testScope = TestScope(UnconfinedTestDispatcher() + Job())

        testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testContext.preferencesDataStoreFile("test_notified_registry_${UUID.randomUUID()}") }
        )

        storage = NotifiedUserStorage(testContext)
    }

    @After
    fun cleanup() = runTest {
        storage.clearAll()
    }

    @Test
    fun testMarkUserAsNotified() = runTest {
        // Given
        val userId = 12345L

        // When
        storage.markAsNotified(userId)

        // Then
        val isNotified = storage.isNotified(userId)
        assertThat(isNotified).isTrue()
    }

    @Test
    fun testUserNotNotifiedByDefault() = runTest {
        // Given
        val userId = 99999L

        // When - non facciamo nulla

        // Then
        val isNotified = storage.isNotified(userId)
        assertThat(isNotified).isFalse()
    }

    @Test
    fun testMultipleUsersNotified() = runTest {
        // Given
        val userId1 = 111L
        val userId2 = 222L
        val userId3 = 333L

        // When
        storage.markAsNotified(userId1)
        storage.markAsNotified(userId2)
        storage.markAsNotified(userId3)

        // Then
        assertThat(storage.isNotified(userId1)).isTrue()
        assertThat(storage.isNotified(userId2)).isTrue()
        assertThat(storage.isNotified(userId3)).isTrue()

        val allNotified = storage.getAllNotifiedIds()
        assertThat(allNotified).containsExactly("111", "222", "333")
    }

    @Test
    fun testGetAllNotifiedIds() = runTest {
        // Given
        storage.markAsNotified(100L)
        storage.markAsNotified(200L)
        storage.markAsNotified(300L)

        // When
        val notifiedIds = storage.getAllNotifiedIds()

        // Then
        assertThat(notifiedIds).hasSize(3)
        assertThat(notifiedIds).contains("100")
        assertThat(notifiedIds).contains("200")
        assertThat(notifiedIds).contains("300")
    }

    @Test
    fun testRemoveNotified() = runTest {
        // Given
        val userId = 54321L
        storage.markAsNotified(userId)
        assertThat(storage.isNotified(userId)).isTrue()

        // When
        storage.removeNotified(userId)

        // Then
        assertThat(storage.isNotified(userId)).isFalse()
    }

    @Test
    fun testClearAll() = runTest {
        // Given
        storage.markAsNotified(1L)
        storage.markAsNotified(2L)
        storage.markAsNotified(3L)
        assertThat(storage.getAllNotifiedIds()).hasSize(3)

        // When
        storage.clearAll()

        // Then
        assertThat(storage.getAllNotifiedIds()).isEmpty()
        assertThat(storage.isNotified(1L)).isFalse()
        assertThat(storage.isNotified(2L)).isFalse()
        assertThat(storage.isNotified(3L)).isFalse()
    }

    @Test
    fun testPersistence() = runTest {
        // Given
        val userId = 777L
        storage.markAsNotified(userId)

        // When - creiamo una nuova istanza dello storage
        val newStorage = NotifiedUserStorage(testContext)

        // Then - i dati dovrebbero essere persistiti
        assertThat(newStorage.isNotified(userId)).isTrue()
    }

    @Test
    fun testDuplicateNotification() = runTest {
        // Given
        val userId = 888L

        // When - marchiamo lo stesso utente due volte
        storage.markAsNotified(userId)
        storage.markAsNotified(userId)

        // Then - dovrebbe essere ancora presente una sola volta
        val allNotified = storage.getAllNotifiedIds()
        assertThat(allNotified.filter { it == "888" }).hasSize(1)
    }

    @Test
    fun testLargeNumberOfUsers() = runTest {
        // Given
        val userCount = 100

        // When
        for (i in 1..userCount) {
            storage.markAsNotified(i.toLong())
        }

        // Then
        val allNotified = storage.getAllNotifiedIds()
        assertThat(allNotified).hasSize(userCount)

        // Verifica alcuni utenti random
        assertThat(storage.isNotified(1L)).isTrue()
        assertThat(storage.isNotified(50L)).isTrue()
        assertThat(storage.isNotified(100L)).isTrue()
        assertThat(storage.isNotified(101L)).isFalse()
    }

    @Test
    fun testMarkUsersAsNotifiedBatch() = runTest {
        // Given
        val userIds = listOf(10L, 20L, 30L)

        // When
        storage.markAsNotifiedBatch(userIds)

        // Then
        assertThat(storage.isNotified(10L)).isTrue()
        assertThat(storage.isNotified(20L)).isTrue()
        assertThat(storage.isNotified(30L)).isTrue()
        assertThat(storage.getAllNotifiedIds()).containsExactly("10", "20", "30")
    }

    @Test
    fun testBatchWriteTiming_smoke() = runTest {
        val userCount = 200
        val userIds = (1..userCount).map { it.toLong() }

        val sequentialMs = measureTimeMillis {
            for (id in userIds) {
                storage.markAsNotified(id)
            }
        }
        storage.clearAll()

        val batchMs = measureTimeMillis {
            storage.markAsNotifiedBatch(userIds)
        }

        val sequentialPerOp = sequentialMs.toDouble() / userCount
        val batchPerOp = batchMs.toDouble() / userCount

        println(
            "NotifiedUserStorage timing (ms): " +
                "users=$userCount, " +
                "sequentialTotal=$sequentialMs, sequentialPerOp=${String.format("%.3f", sequentialPerOp)}, " +
                "batchTotal=$batchMs, batchPerOp=${String.format("%.3f", batchPerOp)}"
        )
        assertThat(storage.getAllNotifiedIds()).hasSize(userCount)
    }
}
