package com.antoninofaro.welcometool.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.antoninofaro.welcometool.di.WelcomedDataStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WelcomedUserStorage @Inject constructor(
    @WelcomedDataStore dataStore: DataStore<Preferences>
) {
    private val store = SetDataStore(dataStore, "welcomed_ids")

    val welcomedIdsFlow: Flow<Set<String>> = store.flow

    suspend fun isWelcomed(userId: Long): Boolean = store.contains(userId.toString())

    suspend fun setWelcomed(userId: Long, isWelcomed: Boolean) {
        if (isWelcomed) store.add(userId.toString())
        else store.remove(userId.toString())
    }

    suspend fun getWelcomedCount(): Int = store.getAll().size

    suspend fun getAllWelcomedIds(): Set<String> = store.getAll()

    suspend fun clearAll() = store.clear()
}
