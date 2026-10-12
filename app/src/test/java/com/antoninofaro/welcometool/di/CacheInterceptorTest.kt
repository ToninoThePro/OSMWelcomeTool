package com.antoninofaro.welcometool.di

import com.antoninofaro.welcometool.data.repository.SettingsRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class CacheInterceptorTest {

    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `OsmCacheControlInterceptor when disabled adds no-cache headers`() {
        val settingsRepository = mock<SettingsRepository> {
            on { isOsmApiCacheEnabledOnce() } doReturn false
        }
        val interceptor = OsmCacheControlInterceptor(settingsRepository)

        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addNetworkInterceptor(interceptor)
            .build()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val request = Request.Builder()
            .url(mockWebServer.url("/api/0.6/user/123"))
            .build()

        val response = client.newCall(request).execute()

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("no-cache", recordedRequest.getHeader("Cache-Control"))
        assertEquals("no-cache, no-store, must-revalidate", response.header("Cache-Control"))
    }

    @Test
    fun `OsmChaCacheControlInterceptor when disabled adds no-cache headers`() {
        val settingsRepository = mock<SettingsRepository> {
            on { isOsmChaCacheEnabledOnce() } doReturn false
        }
        val interceptor = OsmChaCacheControlInterceptor(settingsRepository)

        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addNetworkInterceptor(interceptor)
            .build()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val request = Request.Builder()
            .url(mockWebServer.url("/api/v1/changesets/"))
            .build()

        val response = client.newCall(request).execute()

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("no-cache", recordedRequest.getHeader("Cache-Control"))
        assertEquals("no-cache, no-store, must-revalidate", response.header("Cache-Control"))
    }

    @Test
    fun `NominatimCacheControlInterceptor when disabled adds no-cache headers`() {
        val settingsRepository = mock<SettingsRepository> {
            on { isNominatimCacheEnabledOnce() } doReturn false
        }
        val interceptor = NominatimCacheControlInterceptor(settingsRepository)

        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addNetworkInterceptor(interceptor)
            .build()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val request = Request.Builder()
            .url(mockWebServer.url("/search"))
            .build()

        val response = client.newCall(request).execute()

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("no-cache", recordedRequest.getHeader("Cache-Control"))
        assertEquals("no-cache, no-store, must-revalidate", response.header("Cache-Control"))
    }
}