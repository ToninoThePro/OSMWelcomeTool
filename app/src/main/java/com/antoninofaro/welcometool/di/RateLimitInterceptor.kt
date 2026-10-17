package com.antoninofaro.welcometool.di

import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class RateLimitException(
    val retryAfterSeconds: Long,
    message: String
) : Exception(message)

class RateLimitInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (response.code == 429 || response.code == 503) {
            val retryAfterHeader = response.header("Retry-After")
            val retryAfterSeconds = parseRetryAfter(retryAfterHeader) ?: 60L

            Timber.w(
                "HTTP %d from %s, retry after %ds",
                response.code,
                chain.request().url.host,
                retryAfterSeconds
            )

            response.close()
            throw RateLimitException(
                retryAfterSeconds = retryAfterSeconds,
                message = "Rate limited (HTTP ${response.code}) by ${chain.request().url.host}"
            )
        }

        return response
    }

    private fun parseRetryAfter(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        
        // Try parsing as seconds
        val asSeconds = header.toLongOrNull()
        if (asSeconds != null && asSeconds > 0) return asSeconds
        
        // Try parsing as HTTP-date
        return try {
            val date = ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME)
            val now = ZonedDateTime.now(java.time.ZoneOffset.UTC)
            val diff = ChronoUnit.SECONDS.between(now, date)
            if (diff > 0) diff else null
        } catch (e: Exception) {
            null
        }
    }
}
