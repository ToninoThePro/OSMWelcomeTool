package com.antoninofaro.welcometool.di

import com.antoninofaro.welcometool.data.repository.SettingsRepository
import com.google.common.truth.Truth.assertThat
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OsmCacheControlInterceptorTest {

    @Test
    fun testInterceptor_whenCacheDisabled_addsNoStoreToResponse() {
        // Setup mock
        val settingsRepository: SettingsRepository = mock()
        whenever(settingsRepository.isOsmApiCacheEnabledOnce()).thenReturn(false)
        
        val interceptor = OsmCacheControlInterceptor(settingsRepository)
        
        val originalRequest = Request.Builder().url("https://api.openstreetmap.org/api/0.6/user/123").build()
        val originalResponse = Response.Builder()
            .request(originalRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()
            
        val chain: Interceptor.Chain = mock()
        whenever(chain.request()).thenReturn(originalRequest)
        // It modifies request with no-cache and calls proceed
        whenever(chain.proceed(org.mockito.kotlin.any())).thenReturn(originalResponse)
        
        val finalResponse = interceptor.intercept(chain)
        
        assertThat(finalResponse.header("Cache-Control")).isEqualTo("no-cache, no-store, must-revalidate")
        assertThat(finalResponse.header("Pragma")).isEqualTo("no-cache")
    }
}
