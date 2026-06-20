package com.antoninofaro.welcometool.di

import com.antoninofaro.welcometool.BuildConfig
import com.antoninofaro.welcometool.data.network.NominatimApiService
import com.antoninofaro.welcometool.data.network.OsmApiService
import com.antoninofaro.welcometool.data.network.OsmChaService
import com.antoninofaro.welcometool.data.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module responsible for providing singleton instances of Retrofit API services and OkHttpClient configurations.
 * Configures specific timeouts, intercepters for logging and authorization, and caching mechanisms
 * for interacting with OpenStreetMap, OSMCha, and Nominatim APIs.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val OSMCHA_TIMEOUT = 60L
    private const val NOMINATIM_TIMEOUT = 30L
    private const val OSM_API_BASE_URL = "https://api.openstreetmap.org/"
    private const val OSM_CONNECT_TIMEOUT = 30L
    private const val OSM_READ_TIMEOUT = 30L
    private const val OSM_WRITE_TIMEOUT = 30L
    private const val CACHE_SIZE = 10L * 1024 * 1024 // 10 MB

    /**
     * Provides the main OpenStreetMap API service.
     * Includes an HTTP cache directory setup to optimize bandwidth and connection limits on standard operations.
     */
    @Provides
    @Singleton
    fun provideOsmApiService(@dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context): OsmApiService {
        val cache = okhttp3.Cache(java.io.File(context.cacheDir, "http_cache"), CACHE_SIZE)
        val builder = OkHttpClient.Builder()
            .connectTimeout(OSM_CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(OSM_READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(OSM_WRITE_TIMEOUT, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .cache(cache)

        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            builder.addInterceptor(loggingInterceptor)
        }

        val retrofit = Retrofit.Builder()
            .baseUrl(OSM_API_BASE_URL)
            .client(builder.build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(OsmApiService::class.java)
    }

    /**
     * Provides the OSMCha API service used for reviewing changesets.
     * Configures a dynamic interceptor to append the authorization token from the local [SettingsRepository].
     */
    @Provides
    @Singleton
    fun provideOsmChaService(
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
        settingsRepository: SettingsRepository
    ): OsmChaService {
        val cache = okhttp3.Cache(java.io.File(context.cacheDir, "osmcha_cache"), CACHE_SIZE)
        val client = OkHttpClient.Builder()
            .connectTimeout(OSMCHA_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(OSMCHA_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(OSMCHA_TIMEOUT, TimeUnit.SECONDS)
            .cache(cache)
            .apply {
                if (BuildConfig.DEBUG) {
                    val logging = HttpLoggingInterceptor { message ->
                        Timber.tag("OSMChaHttp").d(message)
                    }.apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                        redactHeader("Authorization")
                    }
                    addInterceptor(logging)
                }
            }
            .addInterceptor { chain ->
                val token = settingsRepository.getOsmchaTokenOnce()

                val original = chain.request()
                if (token.isBlank()) {
                    Timber.w("OSMCha token missing; requests will return 401")
                    return@addInterceptor chain.proceed(original)
                }
                Timber.d("OSMCha token found, adding Authorization header")
                val authed = original.newBuilder()
                    .header("Authorization", "Token $token")
                    .build()
                chain.proceed(authed)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl("https://osmcha.org/api/v1/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OsmChaService::class.java)
    }

    /**
     * Provides the Nominatim API service for geocoding operations.
     * Injects a custom User-Agent header as required by the Nominatim usage policy.
     */
    @Provides
    @Singleton
    fun provideNominatimApiService(
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context
    ): NominatimApiService {
        val cache = okhttp3.Cache(java.io.File(context.cacheDir, "nominatim_cache"), CACHE_SIZE)
        val client = OkHttpClient.Builder()
            .connectTimeout(NOMINATIM_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(NOMINATIM_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(NOMINATIM_TIMEOUT, TimeUnit.SECONDS)
            .cache(cache)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "OSMWelcomeTool/${BuildConfig.VERSION_NAME}"
                    )
                    .build()
                chain.proceed(request)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl("https://nominatim.openstreetmap.org/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NominatimApiService::class.java)
    }
}
