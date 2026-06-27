package com.antoninofaro.welcometool.utils

object TokenMasker {
    /**
     * Masks a token to show only the first and last few characters.
     * Example: "1234567890abcdef" -> "1234...cdef"
     */
    fun mask(token: String?): String {
        if (token.isNullOrBlank()) return "empty"
        if (token.length <= 8) return "****"

        val prefix = token.take(4)
        val suffix = token.takeLast(4)
        return "$prefix...$suffix"
    }

    /**
     * Redacts the Authorization header value if it contains a token.
     */
    fun redactHeader(name: String, value: String): String {
        return if (name.equals("Authorization", ignoreCase = true) && value.startsWith(
                "Token ",
                ignoreCase = true
            )
        ) {
            val token = value.removePrefix("Token ").trim()
            "Token ${mask(token)}"
        } else {
            value
        }
    }
}
