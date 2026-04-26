package com.antoninofaro.welcometool.data.repository

import com.google.common.truth.Truth.assertThat
import com.antoninofaro.welcometool.data.network.OsmChaResponse
import com.antoninofaro.welcometool.data.network.OsmChaService
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import retrofit2.HttpException
import retrofit2.Response

class OsmChaRepositoryTest {

    @Mock
    private lateinit var osmChaService: OsmChaService

    @Mock
    private lateinit var settingsRepository: SettingsRepository

    private lateinit var repository: OsmChaRepositoryImpl

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        // Mock the settings flow to return default AppSettings
        `when`(settingsRepository.settingsFlow).thenReturn(flowOf(AppSettings()))
        repository = OsmChaRepositoryImpl(osmChaService, settingsRepository)
    }

    @Test
    fun `getUserOsmChaStats returns correct counts for Antonino_Faro`() = runTest {
        // Given
        val username = "Antonino_Faro"
        val mockToken = "mock_token"
        `when`(settingsRepository.getOsmchaTokenOnce()).thenReturn(mockToken)

        // Mock response with mixed features
        val mockFeatures = listOf(
            com.antoninofaro.welcometool.data.network.OsmChaFeature(
                properties = com.antoninofaro.welcometool.data.network.OsmChaProperties(
                    checkUser = "user1",
                    isChecked = true,
                    harmful = false  // Like
                )
            ),
            com.antoninofaro.welcometool.data.network.OsmChaFeature(
                properties = com.antoninofaro.welcometool.data.network.OsmChaProperties(
                    checkUser = "user2",
                    isChecked = true,
                    harmful = false  // Like
                )
            ),
            com.antoninofaro.welcometool.data.network.OsmChaFeature(
                properties = com.antoninofaro.welcometool.data.network.OsmChaProperties(
                    checkUser = "user3",
                    isChecked = false,
                    harmful = true  // Dislike
                )
            ),
            com.antoninofaro.welcometool.data.network.OsmChaFeature(
                properties = com.antoninofaro.welcometool.data.network.OsmChaProperties(
                    checkUser = "user4",
                    isChecked = true,
                    harmful = true  // Dislike
                )
            ),
            com.antoninofaro.welcometool.data.network.OsmChaFeature(
                properties = com.antoninofaro.welcometool.data.network.OsmChaProperties(
                    checkUser = null,
                    isChecked = false,
                    harmful = null  // Unchecked/Unknown
                )
            )
        )
        val response = OsmChaResponse(count = 5, features = mockFeatures)
        `when`(osmChaService.getUserChangesets(username)).thenReturn(response)

        // When
        val result = repository.getUserOsmChaStats(username)

        // Then
        assertThat(result.first).isEqualTo(2)  // Likes: features with isChecked=true and harmful=false
        assertThat(result.second).isEqualTo(2) // Dislikes: features with harmful=true
    }

    @Test
    fun `getUserOsmChaStats handles 401 error gracefully`() = runTest {
        // Given
        val username = "Antonino_Faro"
        val mockToken = "invalid_token"
        `when`(settingsRepository.getOsmchaTokenOnce()).thenReturn(mockToken)

        // Simulate 401 error
        val errorResponse = Response.error<OsmChaResponse>(401, "".toResponseBody())
        val exception = HttpException(errorResponse)
        `when`(osmChaService.getUserChangesets(username)).thenThrow(exception)

        // When
        val result = repository.getUserOsmChaStats(username)

        // Then
        assertThat(result.first).isEqualTo(0)
        assertThat(result.second).isEqualTo(0)
    }

    @Test
    fun `getUserOsmChaStats handles generic exception gracefully`() = runTest {
        // Given
        val username = "Antonino_Faro"
        val mockToken = "mock_token"
        `when`(settingsRepository.getOsmchaTokenOnce()).thenReturn(mockToken)

        // Simulate network error
        `when`(osmChaService.getUserChangesets(username)).thenThrow(RuntimeException("Network error"))

        // When
        val result = repository.getUserOsmChaStats(username)

        // Then
        assertThat(result.first).isEqualTo(0)
        assertThat(result.second).isEqualTo(0)
    }
}
