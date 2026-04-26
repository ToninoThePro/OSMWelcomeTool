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

private val Context.notifiedDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "osm_notified_registry"
)

@Singleton
class NotifiedUserStorage @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val notifiedIdsKey = stringSetPreferencesKey("notified_ids")

    /**
     * Flow per osservare gli ID degli utenti notificati
     */
    val notifiedIdsFlow: Flow<Set<String>> = context.notifiedDataStore.data
        .map { preferences ->
            preferences[notifiedIdsKey] ?: emptySet()
        }

    /**
     * Verifica se un utente è già stato notificato
     */
    suspend fun isNotified(userId: Long): Boolean {
        return try {
            val notifiedSet = notifiedIdsFlow.first()
            notifiedSet.contains(userId.toString())
        } catch (e: Exception) {
            Timber.e(e, "Error checking if user $userId is notified")
            false
        }
    }

    /**
     * Marca un utente come notificato
     */
    suspend fun markAsNotified(userId: Long) {
        try {
            context.notifiedDataStore.edit { preferences ->
                val currentSet = preferences[notifiedIdsKey]?.toMutableSet() ?: mutableSetOf()
                currentSet.add(userId.toString())
                preferences[notifiedIdsKey] = currentSet
            }
            Timber.d("User $userId marked as notified")
        } catch (e: Exception) {
            Timber.e(e, "Error marking user $userId as notified")
        }
    }

    /**
     * Marca piu' utenti come notificati in un'unica write
     */
    suspend fun markAsNotifiedBatch(userIds: Collection<Long>) {
        if (userIds.isEmpty()) {
            return
        }
        try {
            context.notifiedDataStore.edit { preferences ->
                val currentSet = preferences[notifiedIdsKey]?.toMutableSet() ?: mutableSetOf()
                userIds.forEach { currentSet.add(it.toString()) }
                preferences[notifiedIdsKey] = currentSet
            }
            Timber.d("Marked ${userIds.size} users as notified")
        } catch (e: Exception) {
            Timber.e(e, "Error marking users as notified")
        }
    }

    /**
     * Ottiene tutti gli ID degli utenti notificati
     */
    suspend fun getAllNotifiedIds(): Set<String> {
        return try {
            notifiedIdsFlow.first()
        } catch (e: Exception) {
            Timber.e(e, "Error getting all notified IDs")
            emptySet()
        }
    }

    /**
     * Rimuove un utente dalla lista dei notificati (per testing)
     */
    suspend fun removeNotified(userId: Long) {
        try {
            context.notifiedDataStore.edit { preferences ->
                val currentSet = preferences[notifiedIdsKey]?.toMutableSet() ?: mutableSetOf()
                currentSet.remove(userId.toString())
                preferences[notifiedIdsKey] = currentSet
            }
            Timber.d("User $userId removed from notified list")
        } catch (e: Exception) {
            Timber.e(e, "Error removing user $userId from notified list")
        }
    }

    /**
     * Rimuove tutti gli utenti notificati (per testing o reset)
     */
    suspend fun clearAll() {
        try {
            context.notifiedDataStore.edit { preferences ->
                preferences.remove(notifiedIdsKey)
            }
            Timber.d("Cleared all notified users")
        } catch (e: Exception) {
            Timber.e(e, "Error clearing notified users")
        }
    }
}
