package com.antoninofaro.welcometool.di

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class RateLimitInterceptorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        client = OkHttpClient.Builder()
            .addInterceptor(RateLimitInterceptor())
            .build()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `429 throws RateLimitException with Retry-After header`() {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(429).setHeader("Retry-After", "5")
        )

        var exception: RateLimitException? = null
        try {
            client.newCall(okhttp3.Request.Builder().url(mockWebServer.url("/")).build()).execute()
        } catch (e: RateLimitException) {
            exception = e
        }

        assertThat(exception).isNotNull()
        assertThat(exception!!.retryAfterSeconds).isEqualTo(5L)
        assertThat(mockWebServer.requestCount).isEqualTo(1)
    }

    @Test
    fun `429 without Retry-After header throws RateLimitException with default backoff`() {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(429)
        )

        var exception: RateLimitException? = null
        try {
            client.newCall(okhttp3.Request.Builder().url(mockWebServer.url("/")).build()).execute()
        } catch (e: RateLimitException) {
            exception = e
        }

        assertThat(exception).isNotNull()
        assertThat(exception!!.retryAfterSeconds).isEqualTo(60L)
        assertThat(mockWebServer.requestCount).isEqualTo(1)
    }

    @Test
    fun `429 with Retry-After 0 uses default backoff`() {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(429).setHeader("Retry-After", "0")
        )

        var exception: RateLimitException? = null
        try {
            client.newCall(okhttp3.Request.Builder().url(mockWebServer.url("/")).build()).execute()
        } catch (e: RateLimitException) {
            exception = e
        }

        // Retry-After: 0 should be treated as missing, use default 60s
        assertThat(exception).isNotNull()
        assertThat(exception!!.retryAfterSeconds).isEqualTo(60L)
        assertThat(mockWebServer.requestCount).isEqualTo(1)
    }

    @Test
    fun `200 response passes through without exception`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        val response = client.newCall(
            okhttp3.Request.Builder().url(mockWebServer.url("/")).build()
        ).execute()

        assertThat(response.code).isEqualTo(200)
        assertThat(response.body?.string()).isEqualTo("ok")
    }

    @Test
    fun `500 response passes through without exception`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val response = client.newCall(
            okhttp3.Request.Builder().url(mockWebServer.url("/")).build()
        ).execute()

        assertThat(response.code).isEqualTo(500)
    }
}
