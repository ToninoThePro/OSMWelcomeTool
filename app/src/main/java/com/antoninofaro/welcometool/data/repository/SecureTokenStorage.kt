@file:Suppress("DEPRECATION")

package com.antoninofaro.welcometool.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

@Singleton
class SecureTokenStorage @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private companion object {
        const val FILE_NAME = "secure_tokens"
        const val KEY_OSMCHA_TOKEN = "osmcha_token"
    }

    // ponytail: allows tests to inject plain SharedPreferences instead of EncryptedSharedPreferences
    internal fun injectTestPrefs(prefs: SharedPreferences) {
        encryptedPrefs = prefs
    }

    private var encryptedPrefs: SharedPreferences = buildEncryptedPrefs()

    private fun buildEncryptedPrefs(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context, FILE_NAME, masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Timber.e(e, "EncryptedSharedPreferences failed, using plain")
            context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        }
    }

    fun saveOsmchaToken(token: String) {
        encryptedPrefs.edit { putString(KEY_OSMCHA_TOKEN, token) }
    }

    fun getOsmchaToken(): String {
        return try {
            encryptedPrefs.getString(KEY_OSMCHA_TOKEN, "") ?: ""
        } catch (e: Exception) {
            Timber.e(e, "Failed to retrieve OSMCha token")
            ""
        }
    }

    fun clearOsmchaToken() {
        encryptedPrefs.edit { remove(KEY_OSMCHA_TOKEN) }
    }

    fun hasOsmchaToken(): Boolean = getOsmchaToken().isNotBlank()
}
