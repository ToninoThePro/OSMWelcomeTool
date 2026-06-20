package com.antoninofaro.welcometool.utils

import java.security.MessageDigest

/**
 * Utility for generating profile image URLs
 */
object AvatarUtils {

    @Suppress("kotlin:S4790")
    fun getGravatarUrl(displayName: String, size: Int = 128): String {
        val normalizedName = displayName.trim().lowercase()
        val email = "$normalizedName@openstreetmap.org"
        val hash = MessageDigest.getInstance("MD5").digest(email.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "https://www.gravatar.com/avatar/$hash?s=$size&d=identicon&r=pg"
    }

    /**
     * Ritorna l'URL dell'immagine profilo.
     * Priorizza l'URL fornito dall'API OSM, altrimenti usa Gravatar con fallback identicon.
     *
     * @param osmImageUrl URL dell'immagine da API OSM
     * @param displayName Nome da usare per Gravatar fallback
     * @return URL valido dell'immagine
     */
    fun getProfileImageUrl(osmImageUrl: String?, displayName: String, size: Int = 128): String? {
        return if (!osmImageUrl.isNullOrBlank()) {
            osmImageUrl
        } else {
            // Usa Gravatar con identicon come fallback
            getGravatarUrl(displayName, size)
        }
    }
}
