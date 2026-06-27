package com.antoninofaro.welcometool.data.network

import com.antoninofaro.welcometool.di.OsmCacheControlInterceptor
import com.antoninofaro.welcometool.di.OsmChaCacheControlInterceptor
import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class CacheHeadersTest {

    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `user profile response gets max-age 86400`() {
        val client = OkHttpClient.Builder()
            .addNetworkInterceptor(OsmCacheControlInterceptor())
            .build()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val response = client.newCall(
            okhttp3.Request.Builder().url(mockWebServer.url("/api/0.6/user/123.json")).build()
        ).execute()

        val cacheControl = response.header("Cache-Control")
        assertThat(cacheControl).isEqualTo("public, max-age=86400")
        assertThat(response.header("Pragma")).isNull()
    }

    @Test
    fun `changesets response gets max-age 300`() {
        val client = OkHttpClient.Builder()
            .addNetworkInterceptor(OsmCacheControlInterceptor())
            .build()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val response = client.newCall(
            okhttp3.Request.Builder()
                .url(mockWebServer.url("/api/0.6/changesets.json?bbox=1,2,3,4")).build()
        ).execute()

        val cacheControl = response.header("Cache-Control")
        assertThat(cacheControl).isEqualTo("public, max-age=300")
        assertThat(response.header("Pragma")).isNull()
    }

    @Test
    fun `other OSM endpoints get no Cache-Control header`() {
        val client = OkHttpClient.Builder()
            .addNetworkInterceptor(OsmCacheControlInterceptor())
            .build()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val response = client.newCall(
            okhttp3.Request.Builder().url(mockWebServer.url("/api/0.6/map.json")).build()
        ).execute()

        assertThat(response.header("Cache-Control")).isNull()
    }

    @Test
    fun `OSMCha response gets max-age 3600`() {
        val client = OkHttpClient.Builder()
            .addNetworkInterceptor(OsmChaCacheControlInterceptor())
            .build()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val response = client.newCall(
            okhttp3.Request.Builder().url(mockWebServer.url("/api/v1/stats/")).build()
        ).execute()

        val cacheControl = response.header("Cache-Control")
        assertThat(cacheControl).isEqualTo("public, max-age=3600")
        assertThat(response.header("Pragma")).isNull()
    }

    @Test
    fun `User-Agent header is present on all requests`() {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "OSMWelcomeTool/Test (Contact: test@example.com)")
                    .build()
                chain.proceed(request)
            }
            .addNetworkInterceptor(OsmCacheControlInterceptor())
            .build()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        client.newCall(
            okhttp3.Request.Builder().url(mockWebServer.url("/api/0.6/user/1.json")).build()
        ).execute()

        val request = mockWebServer.takeRequest()
        assertThat(request.getHeader("User-Agent")).contains("OSMWelcomeTool")
    }
}
