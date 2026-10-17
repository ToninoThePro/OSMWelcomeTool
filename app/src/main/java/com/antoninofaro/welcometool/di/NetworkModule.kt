package com.antoninofaro.welcometool.di

import com.antoninofaro.welcometool.BuildConfig
import com.antoninofaro.welcometool.data.network.NominatimApiService
import com.antoninofaro.welcometool.data.network.OsmApiService
import com.antoninofaro.welcometool.data.network.OsmChaService
import com.antoninofaro.welcometool.data.repository.SettingsRepository
import com.antoninofaro.welcometool.utils.TokenMasker
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
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val OSMCHA_TIMEOUT = 120L
    private const val OSM_CONNECT_TIMEOUT = 30L
    private const val OSM_READ_TIMEOUT = 30L
    private const val OSM_WRITE_TIMEOUT = 30L

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
    fun provideBaseOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(OSM_CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(OSM_READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(OSM_WRITE_TIMEOUT, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(RateLimitInterceptor())
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                if (BuildConfig.DEBUG && originalRequest.url.encodedPath.contains("changesets")) {
                    Timber.tag("OsmChangesetQuery").d("Final URL for /changesets query: %s", originalRequest.url)
                }
                val request = originalRequest.newBuilder()
                    .header(
                        "User-Agent",
                        "OSMWelcomeTool/${BuildConfig.VERSION_NAME} (Contact: farotonino@gmail.com)"
                    )
                    .build()
                chain.proceed(request)
            }

        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                // Basic redaction for the log message itself if it contains headers
                val maskedMessage =
                    if (message.contains("Authorization: Token ", ignoreCase = true)) {
                        val parts = message.split("Authorization: Token ")
                        if (parts.size == 2) {
                            val token = parts[1].trim()
                            "${parts[0]}Authorization: Token ${TokenMasker.mask(token)}"
                        } else message
                    } else message

                Timber.tag("OkHttp").v(maskedMessage)
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
        @BaseClient baseClient: OkHttpClient,
        settingsRepository: SettingsRepository,
        @OsmCache cache: Cache
    ): OkHttpClient {
        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 2
            maxRequestsPerHost = 2
        }
        return baseClient.newBuilder()
            .dispatcher(dispatcher)
            .cache(cache)
            .addNetworkInterceptor(OsmCacheControlInterceptor(settingsRepository))
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
            .baseUrl(BuildConfig.OSM_API_BASE_URL)
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
        settingsRepository: SettingsRepository,
        @OsmChaCache cache: Cache
    ): OkHttpClient {
        return baseClient.newBuilder()
            .cache(cache)
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
            .addNetworkInterceptor(OsmChaCacheControlInterceptor(settingsRepository))
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
            .baseUrl(BuildConfig.OSMCHA_API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(OsmChaService::class.java)
    }

    @Provides
    @Singleton
    @NominatimClient
    fun provideNominatimOkHttpClient(
        @BaseClient baseClient: OkHttpClient,
        settingsRepository: SettingsRepository,
        @NominatimCache cache: Cache
    ): OkHttpClient {
        return baseClient.newBuilder()
            .cache(cache)
            .addNetworkInterceptor(NominatimCacheControlInterceptor(settingsRepository))
            .build()
    }

    @Provides
    @Singleton
    fun provideNominatimApiService(
        @NominatimClient client: OkHttpClient,
        json: Json
    ): NominatimApiService {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.NOMINATIM_API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(NominatimApiService::class.java)
    }
}
