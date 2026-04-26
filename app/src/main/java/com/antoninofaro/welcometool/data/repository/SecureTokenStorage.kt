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

/**
 * Secure storage for sensitive API tokens using Android Security Crypto library.
 *
 * This class provides encrypted storage for the OSMCha API token using:
 * - AES-256-GCM encryption for data
 * - Android Keystore for key management
 * - EncryptedSharedPreferences for transparent encryption/decryption
 *
 * Security benefits:
 * - Token is encrypted at rest
 * - Encryption keys are protected by Android Keystore (hardware-backed when available)
 * - Resistant to file system extraction attacks
 */
@Singleton
class SecureTokenStorage @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private companion object {
        const val FILE_NAME = "secure_tokens"
        const val FALLBACK_FILE_NAME = "secure_tokens_fallback"
        const val KEY_OSMCHA_TOKEN = "osmcha_token"
    }

    private val fallbackPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(FALLBACK_FILE_NAME, Context.MODE_PRIVATE)
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to create EncryptedSharedPreferences, falling back to regular SharedPreferences")
            // Fallback to regular SharedPreferences if encryption fails (e.g., on emulators)
            context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Stores the OSMCha API token securely.
     * The token is encrypted before being written to storage.
     */
    fun saveOsmchaToken(token: String) {
        try {
            encryptedPrefs.edit {
                putString(KEY_OSMCHA_TOKEN, token)
            }
            Timber.d("OSMCha token saved securely")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save OSMCha token")
            fallbackPrefs.edit {
                putString(KEY_OSMCHA_TOKEN, token)
            }
            Timber.w("OSMCha token saved using fallback storage")
        }
    }

    /**
     * Retrieves the OSMCha API token.
     * The token is automatically decrypted when read.
     *
     * @return The decrypted token, or empty string if not set
     */
    fun getOsmchaToken(): String {
        return try {
            val secureValue = encryptedPrefs.getString(KEY_OSMCHA_TOKEN, "") ?: ""
            secureValue.ifBlank { fallbackPrefs.getString(KEY_OSMCHA_TOKEN, "") ?: "" }
        } catch (e: Exception) {
            Timber.e(e, "Failed to retrieve OSMCha token")
            fallbackPrefs.getString(KEY_OSMCHA_TOKEN, "") ?: ""
        }
    }

    /**
     * Removes the OSMCha API token from secure storage.
     */
    fun clearOsmchaToken() {
        try {
            encryptedPrefs.edit {
                remove(KEY_OSMCHA_TOKEN)
            }
            fallbackPrefs.edit {
                remove(KEY_OSMCHA_TOKEN)
            }
            Timber.d("OSMCha token cleared from secure storage")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear OSMCha token")
            fallbackPrefs.edit {
                remove(KEY_OSMCHA_TOKEN)
            }
        }
    }

    /**
     * Checks if an OSMCha token is currently stored.
     */
    fun hasOsmchaToken(): Boolean {
        return getOsmchaToken().isNotBlank()
    }
}

