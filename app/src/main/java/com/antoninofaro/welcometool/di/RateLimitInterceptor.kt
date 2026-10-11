package com.antoninofaro.welcometool.di

import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.min
import kotlin.math.pow

class RateLimitException(
    val retryAfterSeconds: Long,
    message: String
) : Exception(message)

class RateLimitInterceptor : Interceptor {
    companion object {
        private const val MAX_RETRIES = 3
        private const val MAX_BACKOFF_SECONDS = 900L
        private const val DEFAULT_BACKOFF_SECONDS = 60L
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        var response = chain.proceed(chain.request())
        var attempt = 0
        var currentBackoff = DEFAULT_BACKOFF_SECONDS

        while (response.code == 429 && attempt < MAX_RETRIES) {
            attempt++
            
            val retryAfterHeader = response.header("Retry-After")
            val retryAfterSeconds = parseRetryAfter(retryAfterHeader)
            
            val waitSeconds = if (retryAfterSeconds != null) {
                // If the server provides a specific Retry-After, use it
                min(retryAfterSeconds, MAX_BACKOFF_SECONDS)
            } else {
                // Exponential backoff: 60s -> 120s -> 240s
                val exponentialBackoff = DEFAULT_BACKOFF_SECONDS * (2.0.pow(attempt - 1)).toLong()
                min(exponentialBackoff, MAX_BACKOFF_SECONDS)
            }
            
            currentBackoff = waitSeconds

            Timber.w(
                "429 from %s, retry %d/%d after %ds",
                chain.request().url.host,
                attempt,
                MAX_RETRIES,
                waitSeconds
            )
            
            response.close()
            
            try {
                Thread.sleep(waitSeconds * 1000L)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw e
            }

            response = chain.proceed(chain.request())
        }

        if (response.code == 429) {
            response.close()
            throw RateLimitException(
                retryAfterSeconds = currentBackoff,
                message = "Rate limited by ${chain.request().url.host}. Max retries exceeded."
            )
        }

        return response
    }

    private fun parseRetryAfter(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        
        // Try parsing as seconds
        val asSeconds = header.toLongOrNull()
        if (asSeconds != null) return asSeconds
        
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
