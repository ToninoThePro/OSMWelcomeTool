package com.antoninofaro.welcometool.di

import okhttp3.Interceptor
import okhttp3.Response

class OsmChaCacheControlInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        return response.newBuilder()
            .header("Cache-Control", "public, max-age=3600")
            .removeHeader("Pragma")
            .build()
    }
}
