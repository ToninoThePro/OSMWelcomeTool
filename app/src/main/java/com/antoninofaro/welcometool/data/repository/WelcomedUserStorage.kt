package com.antoninofaro.welcometool.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.antoninofaro.welcometool.di.WelcomedDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WelcomedUserStorage @Inject constructor(
    @WelcomedDataStore private val store: DataStore<Preferences>
) {
    private val key = stringSetPreferencesKey("welcomed_ids")
    private val flow: Flow<Set<String>> = store.data.map { it[key] ?: emptySet() }

    suspend fun isWelcomed(userId: Long): Boolean = userId.toString() in flow.first()

    suspend fun setWelcomed(userId: Long, isWelcomed: Boolean) {
        store.edit { prefs ->
            if (isWelcomed) prefs[key] = (prefs[key] ?: emptySet()) + userId.toString()
            else prefs[key] = (prefs[key] ?: emptySet()) - userId.toString()
        }
    }

    suspend fun getAllWelcomedIds(): Set<String> = flow.first()
}
