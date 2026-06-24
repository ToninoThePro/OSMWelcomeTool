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
import java.util.concurrent.ConcurrentHashMap

@Singleton
class SecureTokenStorage @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private companion object {
        const val FILE_NAME = "secure_tokens"
        const val KEY_OSMCHA_TOKEN = "osmcha_token"
    }

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
            Timber.e(e, "EncryptedSharedPreferences failed, using in-memory store")
            inMemoryPrefs
        }
    }

    fun saveOsmchaToken(token: String) {
        Timber.d("SecureTokenStorage: saving token (len=%d)", token.length)
        encryptedPrefs.edit { putString(KEY_OSMCHA_TOKEN, token) }
    }

    fun getOsmchaToken(): String {
        return try {
            val stored = encryptedPrefs.getString(KEY_OSMCHA_TOKEN, "") ?: ""
            Timber.d("SecureTokenStorage: retrieved token (len=%d)", stored.length)
            stored
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

private val inMemoryPrefs = object : SharedPreferences {
    private val map = ConcurrentHashMap<String, String?>()

    override fun getString(key: String?, def: String?) = key?.let { map[it] } ?: def
    override fun contains(key: String?) = key != null && map.containsKey(key)
    override fun getAll() = HashMap(map)

    override fun edit() = Editor()
    override fun getBoolean(k: String?, def: Boolean) = def
    override fun getInt(k: String?, def: Int) = def
    override fun getLong(k: String?, def: Long) = def
    override fun getFloat(k: String?, def: Float) = def
    override fun getStringSet(k: String?, def: MutableSet<String>?) = def
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private inner class Editor : SharedPreferences.Editor {
        private val temp = HashMap<String, String?>()
        override fun putString(k: String?, v: String?) = apply { if (k != null) temp[k] = v }
        override fun remove(k: String?) = apply { if (k != null) temp[k] = null }
        override fun clear() = apply { temp.clear() }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() { map.putAll(temp); temp.clear() }
        override fun putBoolean(k: String?, v: Boolean) = this
        override fun putInt(k: String?, v: Int) = this
        override fun putLong(k: String?, v: Long) = this
        override fun putFloat(k: String?, v: Float) = this
        override fun putStringSet(k: String?, v: MutableSet<String>?) = this
    }
}
