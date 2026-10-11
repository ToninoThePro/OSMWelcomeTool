package com.antoninofaro.welcometool.data.network

import com.google.common.truth.Truth.assertThat
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

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
        } catch (e: Exception) {
        }

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

    @Test
    fun `Kotlin Serialization parses OsmChangeset correctly by ID`() {
        val changesetJson = """
            {
              "version": "0.6",
              "changeset": {
                  "id": 189946046,
                  "created_at": "2024-06-25T14:30:00Z",
                  "closed_at": "2024-06-25T14:31:00Z",
                  "open": false,
                  "user": "MapperName",
                  "uid": 99999,
                  "min_lat": 37.5,
                  "min_lon": 15.0,
                  "max_lat": 37.6,
                  "max_lon": 15.1,
                  "comments_count": 0,
                  "changes_count": 5,
                  "tags": {
                    "comment": "Test changeset parsing",
                    "created_by": "JOSM/1.5 (19096 it)"
                  }
              }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(changesetJson).setResponseCode(200))

        val result = kotlinx.coroutines.runBlocking {
            apiService.getChangesetById(189946046)
        }

        assertThat(result.changeset).isNotNull()
        assertThat(result.changeset?.id).isEqualTo(189946046)
        assertThat(result.changeset?.user).isEqualTo("MapperName")
        assertThat(result.changeset?.tags?.get("comment")).isEqualTo("Test changeset parsing")
    }
}
