package com.antoninofaro.welcometool.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.antoninofaro.welcometool.di.NotifiedDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotifiedUserStorage @Inject constructor(
    @NotifiedDataStore private val store: DataStore<Preferences>
) {
    private val key = stringSetPreferencesKey("notified_ids")
    private val flow: Flow<Set<String>> = store.data.map { it[key] ?: emptySet() }

    suspend fun isNotified(userId: Long): Boolean = userId.toString() in flow.first()

    suspend fun markAsNotified(userId: Long) =
        store.edit { it[key] = (it[key] ?: emptySet()) + userId.toString() }

    suspend fun markAsNotifiedBatch(userIds: Collection<Long>) =
        store.edit { it[key] = (it[key] ?: emptySet()) + userIds.map { it.toString() }.toSet() }

    suspend fun getAllNotifiedIds(): Set<String> = flow.first()

    suspend fun removeNotified(userId: Long) =
        store.edit { it[key] = (it[key] ?: emptySet()) - userId.toString() }

    suspend fun clearAll() =
        store.edit { it.remove(key) }
}
