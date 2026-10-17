package com.antoninofaro.welcometool.di

import com.antoninofaro.welcometool.data.repository.SettingsRepository
import okhttp3.Interceptor
import okhttp3.Response

class OsmCacheControlInterceptor(
    private val cacheEnabledProvider: () -> Boolean = { true }
) : Interceptor {

    constructor(settingsRepository: SettingsRepository) : this({ settingsRepository.isOsmApiCacheEnabledOnce() })

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val isCacheEnabled = cacheEnabledProvider()
        val newRequest = if (!isCacheEnabled) {
            request.newBuilder().header("Cache-Control", "no-cache").build()
        } else {
            request
        }
        val response = chain.proceed(newRequest)
        if (!isCacheEnabled) {
            return response.newBuilder()
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .removeHeader("Expires")
                .build()
        }

        val originalCacheControl = response.header("Cache-Control")
        val originalExpires = response.header("Expires")
        val originalETag = response.header("ETag")
        val originalLastModified = response.header("Last-Modified")

        val url = request.url.toString()
        val needsDefaultCacheControl = originalCacheControl == null
        val defaultCacheControl = when {
            url.contains("/api/0.6/user/") && needsDefaultCacheControl -> "public, max-age=86400"
            url.contains("/api/0.6/changesets.json") && needsDefaultCacheControl -> "public, max-age=300"
            url.contains("/download") && needsDefaultCacheControl -> "public, max-age=31536000" // Cache .osc files for a long time
            else -> null
        }

        return if (defaultCacheControl != null) {
            response.newBuilder()
                .header("Cache-Control", defaultCacheControl)
                .removeHeader("Pragma")
                .build()
        } else {
            response
        }
    }
}
