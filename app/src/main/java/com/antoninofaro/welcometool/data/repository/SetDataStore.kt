package com.antoninofaro.welcometool.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SetDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val keyName: String
) {
    private val key = stringSetPreferencesKey(keyName)

    val flow: Flow<Set<String>> = dataStore.data.map { it[key] ?: emptySet() }

    suspend fun getAll(): Set<String> = flow.first()

    suspend fun contains(id: String): Boolean = id in getAll()

    suspend fun add(id: String) {
        dataStore.edit { it[key] = (it[key] ?: emptySet()) + id }
    }

    suspend fun addAll(ids: Collection<String>) {
        if (ids.isEmpty()) return
        dataStore.edit { it[key] = (it[key] ?: emptySet()) + ids.toSet() }
    }

    suspend fun remove(id: String) {
        dataStore.edit { it[key] = (it[key] ?: emptySet()) - id }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(key) }
    }
}
