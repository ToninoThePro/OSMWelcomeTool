package com.antoninofaro.welcometool.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class NotifiedUserStorageTest {

    private lateinit var storage: NotifiedUserStorage

    @Before
    fun setup() {
        storage = NotifiedUserStorage(InMemoryDataStore())
    }

    @Test
    fun `empty store reports no notifications`() = runTest {
        assertThat(storage.getAllNotifiedIds()).isEmpty()
        assertThat(storage.isNotified(1L)).isFalse()
    }

    @Test
    fun `markAsNotified persists a single user`() = runTest {
        storage.markAsNotified(42L)
        assertThat(storage.isNotified(42L)).isTrue()
        assertThat(storage.getAllNotifiedIds()).containsExactly("42")
    }

    @Test
    fun `markAsNotifiedBatch persists multiple users`() = runTest {
        storage.markAsNotifiedBatch(listOf(1L, 2L, 3L))
        assertThat(storage.getAllNotifiedIds()).containsExactly("1", "2", "3")
    }

    @Test
    fun `markAsNotifiedBatch is idempotent`() = runTest {
        storage.markAsNotifiedBatch(listOf(1L, 2L))
        storage.markAsNotifiedBatch(listOf(2L, 3L))
        assertThat(storage.getAllNotifiedIds()).containsExactly("1", "2", "3")
    }

    @Test
    fun `removeNotified removes a single user`() = runTest {
        storage.markAsNotified(1L)
        storage.markAsNotified(2L)
        storage.removeNotified(1L)
        assertThat(storage.getAllNotifiedIds()).containsExactly("2")
    }

    @Test
    fun `clearAll empties the store`() = runTest {
        storage.markAsNotifiedBatch(listOf(1L, 2L, 3L))
        storage.clearAll()
        assertThat(storage.getAllNotifiedIds()).isEmpty()
    }

    @Test
    fun `duplicate markAsNotified does not duplicate entry`() = runTest {
        storage.markAsNotified(1L)
        storage.markAsNotified(1L)
        assertThat(storage.getAllNotifiedIds()).hasSize(1)
    }

    @Test
    fun `markAsNotifiedBatch with empty list does nothing`() = runTest {
        storage.markAsNotifiedBatch(emptyList())
        assertThat(storage.getAllNotifiedIds()).isEmpty()
    }
}

private val NOTIFIED_KEY = stringSetPreferencesKey("notified_ids")

private class InMemoryDataStore : DataStore<Preferences> {
    private val _data = MutableStateFlow(mutablePreferencesOf())
    override val data: Flow<Preferences> = _data

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val current = _data.value
        val updated = transform(current)
        _data.value = updated.toMutablePreferences()
        return _data.value
    }
}
