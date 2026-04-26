package com.antoninofaro.welcometool.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val Context.welcomedDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "osm_welcomed_registry"
)

@Singleton
class WelcomedUserStorage @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val welcomedIdsKey = stringSetPreferencesKey("welcomed_ids")

    /**
     * Flow per osservare gli ID degli utenti benvenuti
     */
    val welcomedIdsFlow: Flow<Set<String>> = context.welcomedDataStore.data
        .map { preferences ->
            preferences[welcomedIdsKey] ?: emptySet()
        }

    /**
     * Verifica se un utente è stato benvenuto
     */
    suspend fun isWelcomed(userId: Long): Boolean {
        return try {
            val welcomedSet = welcomedIdsFlow.first()
            welcomedSet.contains(userId.toString())
        } catch (e: Exception) {
            Timber.e(e, "Error checking if user $userId is welcomed")
            false
        }
    }

    /**
     * Imposta lo stato di benvenuto per un utente
     */
    suspend fun setWelcomed(userId: Long, isWelcomed: Boolean) {
        try {
            context.welcomedDataStore.edit { preferences ->
                val currentSet = preferences[welcomedIdsKey]?.toMutableSet() ?: mutableSetOf()

                if (isWelcomed) {
                    currentSet.add(userId.toString())
                } else {
                    currentSet.remove(userId.toString())
                }

                preferences[welcomedIdsKey] = currentSet
            }
            Timber.d("User $userId welcomed status set to $isWelcomed")
        } catch (e: Exception) {
            Timber.e(e, "Error setting welcomed status for user $userId")
        }
    }

    /**
     * Ottiene il conteggio degli utenti benvenuti
     */
    suspend fun getWelcomedCount(): Int {
        return try {
            welcomedIdsFlow.first().size
        } catch (e: Exception) {
            Timber.e(e, "Error getting welcomed count")
            0
        }
    }

    /**
     * Ottiene tutti gli ID degli utenti benvenuti
     */
    suspend fun getAllWelcomedIds(): Set<String> {
        return try {
            welcomedIdsFlow.first()
        } catch (e: Exception) {
            Timber.e(e, "Error getting all welcomed IDs")
            emptySet()
        }
    }

    /**
     * Rimuove tutti gli utenti benvenuti (per testing o reset)
     */
    suspend fun clearAll() {
        try {
            context.welcomedDataStore.edit { preferences ->
                preferences.remove(welcomedIdsKey)
            }
            Timber.d("Cleared all welcomed users")
        } catch (e: Exception) {
            Timber.e(e, "Error clearing welcomed users")
        }
    }
}
