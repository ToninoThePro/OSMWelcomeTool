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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.system.measureTimeMillis
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NotifiedUserStorageTest {

    private lateinit var testContext: Context
    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var storage: NotifiedUserStorage
    private lateinit var scope: TestScope

    @Before
    fun setup() {
        testContext = ApplicationProvider.getApplicationContext()
        scope = TestScope(UnconfinedTestDispatcher())
        testDataStore = PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { testContext.preferencesDataStoreFile("test_notified_${UUID.randomUUID()}") }
        )
        storage = NotifiedUserStorage(testDataStore)
    }

    @After
    fun cleanup() = runTest {
        storage.clearAll()
    }

    @Test
    fun testMarkUserAsNotified() = runTest {
        storage.markAsNotified(12345L)
        assertThat(storage.isNotified(12345L)).isTrue()
    }

    @Test
    fun testUserNotNotifiedByDefault() = runTest {
        assertThat(storage.isNotified(99999L)).isFalse()
    }

    @Test
    fun testMultipleUsersNotified() = runTest {
        storage.markAsNotified(111L)
        storage.markAsNotified(222L)
        storage.markAsNotified(333L)
        assertThat(storage.isNotified(111L)).isTrue()
        assertThat(storage.isNotified(222L)).isTrue()
        assertThat(storage.isNotified(333L)).isTrue()
        assertThat(storage.getAllNotifiedIds()).containsExactly("111", "222", "333")
    }

    @Test
    fun testGetAllNotifiedIds() = runTest {
        storage.markAsNotified(100L)
        storage.markAsNotified(200L)
        storage.markAsNotified(300L)
        val ids = storage.getAllNotifiedIds()
        assertThat(ids).hasSize(3)
        assertThat(ids).contains("100")
        assertThat(ids).contains("200")
        assertThat(ids).contains("300")
    }

    @Test
    fun testRemoveNotified() = runTest {
        storage.markAsNotified(54321L)
        assertThat(storage.isNotified(54321L)).isTrue()
        storage.removeNotified(54321L)
        assertThat(storage.isNotified(54321L)).isFalse()
    }

    @Test
    fun testClearAll() = runTest {
        storage.markAsNotified(1L)
        storage.markAsNotified(2L)
        storage.markAsNotified(3L)
        assertThat(storage.getAllNotifiedIds()).hasSize(3)
        storage.clearAll()
        assertThat(storage.getAllNotifiedIds()).isEmpty()
        assertThat(storage.isNotified(1L)).isFalse()
        assertThat(storage.isNotified(2L)).isFalse()
        assertThat(storage.isNotified(3L)).isFalse()
    }

    @Test
    fun testPersistence() = runTest {
        storage.markAsNotified(777L)
        val newStorage = NotifiedUserStorage(testDataStore)
        assertThat(newStorage.isNotified(777L)).isTrue()
    }

    @Test
    fun testDuplicateNotification() = runTest {
        storage.markAsNotified(888L)
        storage.markAsNotified(888L)
        assertThat(storage.getAllNotifiedIds().filter { it == "888" }).hasSize(1)
    }

    @Test
    fun testLargeNumberOfUsers() = runTest {
        val count = 100
        for (i in 1..count) storage.markAsNotified(i.toLong())
        assertThat(storage.getAllNotifiedIds()).hasSize(count)
        assertThat(storage.isNotified(1L)).isTrue()
        assertThat(storage.isNotified(50L)).isTrue()
        assertThat(storage.isNotified(100L)).isTrue()
        assertThat(storage.isNotified(101L)).isFalse()
    }

    @Test
    fun testMarkUsersAsNotifiedBatch() = runTest {
        storage.markAsNotifiedBatch(listOf(10L, 20L, 30L))
        assertThat(storage.isNotified(10L)).isTrue()
        assertThat(storage.isNotified(20L)).isTrue()
        assertThat(storage.isNotified(30L)).isTrue()
        assertThat(storage.getAllNotifiedIds()).containsExactly("10", "20", "30")
    }

    @Test
    fun testBatchWriteTiming_smoke() = runTest {
        val count = 200
        val ids = (1..count).map { it.toLong() }
        measureTimeMillis { for (id in ids) storage.markAsNotified(id) }
        storage.clearAll()
        measureTimeMillis { storage.markAsNotifiedBatch(ids) }
        assertThat(storage.getAllNotifiedIds()).hasSize(count)
    }
}
