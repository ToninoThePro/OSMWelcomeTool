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
    fun `429 with Retry-After header retries and eventually succeeds`() {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(429).setHeader("Retry-After", "1")
        )
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody("success")
        )

        val response = client.newCall(
            okhttp3.Request.Builder().url(mockWebServer.url("/")).build()
        ).execute()

        assertThat(response.code).isEqualTo(200)
        assertThat(response.body?.string()).isEqualTo("success")
        assertThat(mockWebServer.requestCount).isEqualTo(2)
    }

    @Test
    fun `429 throws RateLimitException after max retries`() {
        // Enqueue 4 429 responses (initial + 3 retries)
        for (i in 0 until 4) {
            mockWebServer.enqueue(
                MockResponse().setResponseCode(429).setHeader("Retry-After", "0")
            )
        }

        val exception = runCatching {
            client.newCall(okhttp3.Request.Builder().url(mockWebServer.url("/")).build()).execute()
        }.exceptionOrNull()

        assertThat(exception).isInstanceOf(RateLimitException::class.java)
        // With Retry-After: 0, backoff should use that value for currentBackoff in the exception
        assertThat(mockWebServer.requestCount).isEqualTo(4)
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
