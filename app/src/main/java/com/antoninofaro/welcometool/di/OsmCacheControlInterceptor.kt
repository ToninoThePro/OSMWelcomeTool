package com.antoninofaro.welcometool.di

import okhttp3.Interceptor
import okhttp3.Response

class OsmCacheControlInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
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
