package com.antoninofaro.welcometool.ui.screens

import android.app.Application
import com.antoninofaro.welcometool.data.cache.CacheCategory
import com.antoninofaro.welcometool.data.cache.CacheRegistry
import com.antoninofaro.welcometool.data.cache.CacheSizeCalculator
import com.antoninofaro.welcometool.data.cache.CacheSizesResult
import com.antoninofaro.welcometool.data.repository.NominatimRepository
import com.antoninofaro.welcometool.data.repository.NotifiedUserStorage
import com.antoninofaro.welcometool.data.repository.OsmChaRepository
import com.antoninofaro.welcometool.data.repository.SettingsRepository
import com.antoninofaro.welcometool.utils.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelCacheTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // TODO: Fix suspend function mocking - CacheSizeCalculator.calculateAllSizes is final
// @Test
// fun testClearAllCaches() = runTest { ... }
}