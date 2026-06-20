package com.antoninofaro.welcometool.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.antoninofaro.welcometool.di.NotifiedDataStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotifiedUserStorage @Inject constructor(
    @NotifiedDataStore dataStore: DataStore<Preferences>
) {
    private val store = SetDataStore(dataStore, "notified_ids")

    val notifiedIdsFlow: Flow<Set<String>> = store.flow

    suspend fun isNotified(userId: Long): Boolean = store.contains(userId.toString())

    suspend fun markAsNotified(userId: Long) = store.add(userId.toString())

    suspend fun markAsNotifiedBatch(userIds: Collection<Long>) =
        store.addAll(userIds.map { it.toString() })

    suspend fun getAllNotifiedIds(): Set<String> = store.getAll()

    suspend fun removeNotified(userId: Long) = store.remove(userId.toString())

    suspend fun clearAll() = store.clear()
}
