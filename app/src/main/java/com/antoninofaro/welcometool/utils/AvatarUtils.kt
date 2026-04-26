package com.antoninofaro.welcometool.utils

import java.security.MessageDigest

/**
 * Utility for generating profile image URLs
 */
object AvatarUtils {

    /**
     * Genera un URL Gravatar basato sull'email dell'utente.
     * Poiché l'API OSM non fornisce direttamente l'email, usiamo uno hash generico.
     *
     * NOTA SULLA SICUREZZA: MD5 è utilizzato qui come richiesto dall'API Gravatar.
     * Questo NON è un uso crittografico - MD5 è lo standard documentato da Gravatar
     * per generare hash di email pubblici usati solo per l'indicizzazione delle immagini.
     * Riferimento: https://docs.gravatar.com/api/avatars/hash/
     *
     * @param displayName Nome da usare per generare l'avatar
     * @param size Dimensione dell'avatar in pixel (default 128)
     * @return URL dell'avatar Gravatar
     */
    @Suppress("kotlin:S4790") // MD5 è richiesto dall'API Gravatar - non è un uso crittografico
    fun getGravatarUrl(displayName: String, size: Int = 128): String {
        val normalizedName = displayName.trim().lowercase()
        val email = "$normalizedName@openstreetmap.org"
        // MD5 è richiesto dall'API Gravatar per l'hashing delle email
        val hash = email
            .let { MessageDigest.getInstance("MD5").digest(it.toByteArray()) }
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
