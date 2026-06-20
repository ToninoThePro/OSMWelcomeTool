package com.antoninofaro.welcometool

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import com.google.common.truth.Truth.assertThat
import com.antoninofaro.welcometool.data.repository.NotifiedUserStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NotificationSystemIntegrationTest {

    private lateinit var context: Context
    private lateinit var storage: NotifiedUserStorage
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var scope: TestScope

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        scope = TestScope(UnconfinedTestDispatcher())
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { context.preferencesDataStoreFile("test_notified_int_${UUID.randomUUID()}") }
        )
        storage = NotifiedUserStorage(dataStore)
    }

    @After
    fun cleanup() = runTest { storage.clearAll() }

    @Test
    fun testCompleteNotificationFlow() = runTest {
        assertThat(storage.getAllNotifiedIds()).isEmpty()
        storage.markAsNotified(1001L)
        assertThat(storage.isNotified(1001L)).isTrue()
        assertThat(storage.getAllNotifiedIds()).containsExactly("1001")
        assertThat(storage.isNotified(1001L)).isTrue()
        assertThat(storage.isNotified(1002L)).isFalse()
        storage.markAsNotified(1002L)
        assertThat(storage.getAllNotifiedIds()).hasSize(2)
        assertThat(storage.getAllNotifiedIds()).containsExactly("1001", "1002")
    }

    @Test
    fun testMultipleDaysScenario() = runTest {
        listOf(100L, 101L, 102L).forEach { storage.markAsNotified(it) }
        assertThat(storage.getAllNotifiedIds()).hasSize(3)
        listOf(200L, 201L).forEach { assertThat(storage.isNotified(it)).isFalse(); storage.markAsNotified(it) }
        assertThat(storage.getAllNotifiedIds()).hasSize(5)
        listOf(100L, 101L, 102L, 200L, 201L).forEach { assertThat(storage.isNotified(it)).isTrue() }
    }

    @Test
    fun testHighVolumeScenario() = runTest {
        (1L..50L).forEach { if (!storage.isNotified(it)) storage.markAsNotified(it) }
        assertThat(storage.getAllNotifiedIds()).hasSize(50)
        assertThat((1L..50L).count { !storage.isNotified(it) }).isEqualTo(0)
    }

    @Test
    fun testResetScenario() = runTest {
        (1L..3L).forEach { storage.markAsNotified(it) }
        assertThat(storage.getAllNotifiedIds()).hasSize(3)
        storage.clearAll()
        assertThat(storage.getAllNotifiedIds()).isEmpty()
        assertThat(storage.isNotified(1L)).isFalse()
        assertThat(storage.isNotified(2L)).isFalse()
        assertThat(storage.isNotified(3L)).isFalse()
        storage.markAsNotified(100L)
        assertThat(storage.getAllNotifiedIds()).containsExactly("100")
    }

    @Test
    fun testMixedUserTypesScenario() = runTest {
        listOf(1L, 2L, 3L).forEach { storage.markAsNotified(it) }
        assertThat(storage.getAllNotifiedIds()).hasSize(3)
        listOf(1L, 2L, 3L).forEach { assertThat(storage.isNotified(it)).isTrue() }
        listOf(100L, 101L, 102L).forEach { assertThat(storage.isNotified(it)).isFalse() }
    }

    @Test
    fun testConcurrentAccessScenario() = runTest {
        if (!storage.isNotified(999L)) storage.markAsNotified(999L)
        assertThat(storage.isNotified(999L)).isTrue()
        assertThat(storage.getAllNotifiedIds().count { it == "999" }).isEqualTo(1)
    }

    @Test
    fun testLongTermUsage() = runTest {
        val total = 200
        val batch = 50
        for (b in 0 until total / batch) {
            (b * batch + 1..b * batch + batch).forEach { storage.markAsNotified(it.toLong()) }
            assertThat(storage.getAllNotifiedIds()).hasSize((b + 1) * batch)
        }
        assertThat(storage.getAllNotifiedIds()).hasSize(total)
        assertThat(storage.isNotified(1L)).isTrue()
        assertThat(storage.isNotified(100L)).isTrue()
        assertThat(storage.isNotified(200L)).isTrue()
        assertThat(storage.isNotified(201L)).isFalse()
    }
}
