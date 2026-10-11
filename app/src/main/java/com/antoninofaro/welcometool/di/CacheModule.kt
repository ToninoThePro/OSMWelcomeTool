package com.antoninofaro.welcometool.di

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.antoninofaro.welcometool.data.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CacheModule {

    @Provides
    @Singleton
    @OsmCache
    fun provideOsmCache(@ApplicationContext context: Context): Cache {
        return Cache(File(context.cacheDir, "osm_api_cache"), 20L * 1024 * 1024)
    }

    @Provides
    @Singleton
    @OsmChaCache
    fun provideOsmChaCache(@ApplicationContext context: Context): Cache {
        return Cache(File(context.cacheDir, "osmcha_cache"), 15L * 1024 * 1024)
    }

    @Provides
    @Singleton
    @NominatimCache
    fun provideNominatimCache(@ApplicationContext context: Context): Cache {
        return Cache(File(context.cacheDir, "nominatim_cache"), 15L * 1024 * 1024)
    }

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        @BaseClient okHttpClient: OkHttpClient,
        settingsRepository: SettingsRepository
    ): ImageLoader {
        val isAvatarCacheEnabled = settingsRepository.isAvatarCacheEnabledOnce()
        
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            }
            .diskCache {
                if (isAvatarCacheEnabled) {
                    DiskCache.Builder()
                        .directory(context.cacheDir.resolve("image_cache"))
                        .maxSizeBytes(20L * 1024 * 1024) // 20 MB max
                        .build()
                } else {
                    null
                }
            }
            .build()
    }
}
