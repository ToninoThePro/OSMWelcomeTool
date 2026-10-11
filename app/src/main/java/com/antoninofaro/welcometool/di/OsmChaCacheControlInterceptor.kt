package com.antoninofaro.welcometool.di

import com.antoninofaro.welcometool.data.repository.SettingsRepository
import okhttp3.Interceptor
import okhttp3.Response

class OsmChaCacheControlInterceptor(
    private val cacheEnabledProvider: () -> Boolean = { true }
) : Interceptor {

    constructor(settingsRepository: SettingsRepository) : this({ settingsRepository.isOsmChaCacheEnabledOnce() })

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
        return response.newBuilder()
            .header("Cache-Control", "public, max-age=3600")
            .removeHeader("Pragma")
            .build()
    }
}
