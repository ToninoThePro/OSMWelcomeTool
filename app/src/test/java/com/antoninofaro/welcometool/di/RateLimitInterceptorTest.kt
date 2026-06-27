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
    fun `429 with Retry-After header throws RateLimitException with correct seconds`() {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(429).setHeader("Retry-After", "120")
        )

        val exception = runCatching {
            client.newCall(okhttp3.Request.Builder().url(mockWebServer.url("/")).build()).execute()
        }.exceptionOrNull()

        assertThat(exception).isInstanceOf(RateLimitException::class.java)
        assertThat((exception as RateLimitException).retryAfterSeconds).isEqualTo(120L)
    }

    @Test
    fun `429 without Retry-After header throws RateLimitException with default 60s`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(429))

        val exception = runCatching {
            client.newCall(okhttp3.Request.Builder().url(mockWebServer.url("/")).build()).execute()
        }.exceptionOrNull()

        assertThat(exception).isInstanceOf(RateLimitException::class.java)
        assertThat((exception as RateLimitException).retryAfterSeconds).isEqualTo(60L)
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
