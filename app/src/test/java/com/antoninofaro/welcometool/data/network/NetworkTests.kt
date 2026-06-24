package com.antoninofaro.welcometool.data.network

import com.antoninofaro.welcometool.data.model.OsmUser
import com.antoninofaro.welcometool.data.model.OsmUserWrapper
import com.antoninofaro.welcometool.data.model.UserImage
import com.antoninofaro.welcometool.data.model.CountWrapper
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient

class NetworkTests {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: OsmApiService
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "OSMWelcomeTool/Test")
                    .build()
                chain.proceed(request)
            }
            .build()

        apiService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OsmApiService::class.java)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `User-Agent header is correctly added`() {
        mockWebServer.enqueue(MockResponse().setBody("{}").setResponseCode(200))
        
        // Use a call that doesn't strictly need a valid JSON for just checking headers
        try {
             kotlinx.coroutines.runBlocking {
                apiService.getUserDetail(123)
            }
        } catch (e: Exception) {}

        val request = mockWebServer.takeRequest()
        assertThat(request.getHeader("User-Agent")).contains("OSMWelcomeTool")
    }

    @Test
    fun `Kotlin Serialization parses OsmUser correctly`() {
        val userJson = """
            {
              "user": {
                "id": 1,
                "display_name": "TestUser",
                "account_created": "2020-01-01T00:00:00Z",
                "description": "Hello",
                "img": { "href": "http://example.com/img.jpg" },
                "changesets": { "count": 100 }
              }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(userJson).setResponseCode(200))

        val result = kotlinx.coroutines.runBlocking {
            apiService.getUserDetail(1)
        }

        assertThat(result.user.displayName).isEqualTo("TestUser")
        assertThat(result.user.changesets?.count).isEqualTo(100)
    }
}
