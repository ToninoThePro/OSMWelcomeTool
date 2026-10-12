package com.antoninofaro.welcometool.data.cache

import android.content.Context
import coil3.imageLoader
import com.antoninofaro.welcometool.R
import com.antoninofaro.welcometool.data.repository.SettingsRepository
import com.antoninofaro.welcometool.di.NominatimCache
import com.antoninofaro.welcometool.di.OsmCache
import com.antoninofaro.welcometool.di.OsmChaCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Cache
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CacheRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    @OsmCache private val osmCache: Cache,
    @OsmChaCache private val osmChaCache: Cache,
    @NominatimCache private val nominatimCache: Cache
) {
    
    fun getCategories(): List<CacheCategory> {
        val settings = runBlocking { settingsRepository.settingsFlow.first() }
        
        return listOf(
            object : CacheCategory {
                override val id = "osm_api"
                override val name = context.getString(R.string.cache_category_osm_api)
                override val isEnabled = settings.cacheEnabledOsmApi
                override val sizeBytes: Long
                    get() = try { osmCache.size() } catch (e: Exception) { 0L }
                override fun clear() { try { osmCache.evictAll() } catch (e: Exception) { } }
            },
            object : CacheCategory {
                override val id = "osmcha"
                override val name = context.getString(R.string.cache_category_osmcha)
                override val isEnabled = settings.cacheEnabledOsmCha
                override val sizeBytes: Long
                    get() = try { osmChaCache.size() } catch (e: Exception) { 0L }
                override fun clear() { try { osmChaCache.evictAll() } catch (e: Exception) { } }
            },
            object : CacheCategory {
                override val id = "nominatim"
                override val name = context.getString(R.string.cache_category_nominatim)
                override val isEnabled = settings.cacheEnabledNominatim
                override val sizeBytes: Long
                    get() = try { nominatimCache.size() } catch (e: Exception) { 0L }
                override fun clear() { try { nominatimCache.evictAll() } catch (e: Exception) { } }
            },
            object : CacheCategory {
                override val id = "avatar"
                override val name = context.getString(R.string.cache_category_avatar)
                override val isEnabled = settings.cacheEnabledAvatar
                override val sizeBytes: Long
                    get() = try { context.imageLoader.diskCache?.size ?: 0L } catch (e: Exception) { 0L }
                override fun clear() { try { context.imageLoader.diskCache?.clear() } catch (e: Exception) { } }
            }
        )
    }
}
