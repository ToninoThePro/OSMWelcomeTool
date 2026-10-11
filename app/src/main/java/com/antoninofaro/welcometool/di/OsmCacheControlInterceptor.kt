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
        val url = request.url.toString()
        val cacheControl = when {
            url.contains("/api/0.6/user/") -> "public, max-age=86400"
            url.contains("/api/0.6/changesets.json") -> "public, max-age=300"
            else -> null
        }
        return if (cacheControl != null) {
            response.newBuilder()
                .header("Cache-Control", cacheControl)
                .removeHeader("Pragma")
                .build()
        } else {
            response
        }
    }
}
