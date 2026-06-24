package com.antoninofaro.welcometool.di

import com.antoninofaro.welcometool.BuildConfig
import com.antoninofaro.welcometool.data.network.NominatimApiService
import com.antoninofaro.welcometool.data.network.OsmApiService
import com.antoninofaro.welcometool.data.network.OsmChaService
import com.antoninofaro.welcometool.data.repository.SettingsRepository
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val OSMCHA_TIMEOUT = 120L
    private const val NOMINATIM_TIMEOUT = 30L
    private const val OSM_API_BASE_URL = "https://api.openstreetmap.org/"
    private const val OSM_CONNECT_TIMEOUT = 30L
    private const val OSM_READ_TIMEOUT = 30L
    private const val OSM_WRITE_TIMEOUT = 30L
    private const val CACHE_SIZE = 50L * 1024 * 1024 // 50 MB

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        isLenient = true
    }

    @Provides
    @Singleton
    @BaseClient
    fun provideBaseOkHttpClient(
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context
    ): OkHttpClient {
        val cache = Cache(File(context.cacheDir, "http_cache"), CACHE_SIZE)
        val builder = OkHttpClient.Builder()
            .connectTimeout(OSM_CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(OSM_READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(OSM_WRITE_TIMEOUT, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .cache(cache)
            .addInterceptor(RateLimitInterceptor())
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "OSMWelcomeTool/${BuildConfig.VERSION_NAME} (Contact: farotonino@gmail.com)")
                    .build()
                chain.proceed(request)
            }

        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                Timber.tag("OkHttp").v(message)
            }.apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
            builder.addInterceptor(loggingInterceptor)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    @OsmClient
    fun provideOsmOkHttpClient(
        @BaseClient baseClient: OkHttpClient
    ): OkHttpClient {
        return baseClient.newBuilder()
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                
                // Smart Caching Strategy
                val url = request.url.toString()
                val cacheControl = when {
                    url.contains("/api/0.6/user/") -> "public, max-age=86400" // User profile: 24h
                    url.contains("/api/0.6/changesets.json") -> "public, max-age=300" // Changesets: 5m
                    else -> null
                }

                if (cacheControl != null) {
                    response.newBuilder()
                        .header("Cache-Control", cacheControl)
                        .removeHeader("Pragma")
                        .build()
                } else {
                    response
                }
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideOsmApiService(
        @OsmClient client: OkHttpClient,
        json: Json
    ): OsmApiService {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(OSM_API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(OsmApiService::class.java)
    }

    @Provides
    @Singleton
    @OsmChaClient
    fun provideOsmChaOkHttpClient(
        @BaseClient baseClient: OkHttpClient,
        settingsRepository: SettingsRepository
    ): OkHttpClient {
        return baseClient.newBuilder()
            .connectTimeout(OSMCHA_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(OSMCHA_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(OSMCHA_TIMEOUT, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val token = settingsRepository.getOsmchaTokenOnce()
                val original = chain.request()
                if (token.isBlank()) {
                    Timber.d("OSMCha: no token available, skipping auth")
                    return@addInterceptor chain.proceed(original)
                }
                Timber.d("OSMCha: adding auth header (token len=%d)", token.length)
                val authed = original.newBuilder()
                    .header("Authorization", "Token $token")
                    .build()
                chain.proceed(authed)
            }
            .addNetworkInterceptor { chain ->
                // Cache OSMCha stats for 1 hour
                val response = chain.proceed(chain.request())
                response.newBuilder()
                    .header("Cache-Control", "public, max-age=3600")
                    .removeHeader("Pragma")
                    .build()
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideOsmChaService(
        @OsmChaClient client: OkHttpClient,
        json: Json
    ): OsmChaService {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://osmcha.org/api/v1/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(OsmChaService::class.java)
    }

    @Provides
    @Singleton
    fun provideNominatimApiService(
        @BaseClient client: OkHttpClient,
        json: Json
    ): NominatimApiService {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl("https://nominatim.openstreetmap.org/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(NominatimApiService::class.java)
    }
}
