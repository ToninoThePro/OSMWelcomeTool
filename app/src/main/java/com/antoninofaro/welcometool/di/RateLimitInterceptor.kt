package com.antoninofaro.welcometool.di

import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber

class RateLimitException(
    val retryAfterSeconds: Long,
    message: String
) : Exception(message)

class RateLimitInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code == 429) {
            val retryAfter = response.header("Retry-After")
            val seconds = retryAfter?.toLongOrNull() ?: 60L
            Timber.w("HTTP 429 rate limited. Retry-After: ${seconds}s — ${chain.request().url}")
            response.close()
            throw RateLimitException(
                retryAfterSeconds = seconds,
                message = "Rate limited by ${chain.request().url.host}. Retry after ${seconds}s."
            )
        }
        return response
    }
}
