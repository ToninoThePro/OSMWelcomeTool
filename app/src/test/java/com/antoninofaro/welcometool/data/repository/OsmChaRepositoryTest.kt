package com.antoninofaro.welcometool.data.repository

import com.antoninofaro.welcometool.data.network.OsmChaFeature
import com.antoninofaro.welcometool.data.network.OsmChaProperties
import com.antoninofaro.welcometool.data.network.OsmChaResponse
import com.antoninofaro.welcometool.data.network.OsmChaService
import com.antoninofaro.welcometool.data.network.OsmChaUserResponse
import com.google.common.truth.Truth.assertThat
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
import java.io.IOException

class OsmChaRepositoryTest {

    @Mock
    private lateinit var osmChaService: OsmChaService

    @Mock
    private lateinit var settingsRepository: SettingsRepository

    private lateinit var repository: OsmChaRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        `when`(settingsRepository.settingsFlow).thenReturn(flowOf(AppSettings()))
        repository = OsmChaRepository(osmChaService, settingsRepository)
    }

    @Test
    fun `getUserOsmChaStats returns correct counts for Antonino_Faro`() = runTest {
        val username = "Antonino_Faro"
        `when`(settingsRepository.getOsmchaTokenOnce()).thenReturn("mock_token")

        val mockFeatures = listOf(
            OsmChaFeature(OsmChaProperties(checkUser = "user1", isChecked = true, harmful = false)),
            OsmChaFeature(OsmChaProperties(checkUser = "user2", isChecked = true, harmful = false)),
            OsmChaFeature(OsmChaProperties(checkUser = "user3", isChecked = false, harmful = true)),
            OsmChaFeature(OsmChaProperties(checkUser = "user4", isChecked = true, harmful = true)),
            OsmChaFeature(OsmChaProperties(checkUser = null, isChecked = false, harmful = null)),
        )
        val response = OsmChaResponse(count = 5, features = mockFeatures)
        `when`(osmChaService.getCheckedChangesets(username, 1, 100)).thenReturn(response)

        val result = repository.getUserOsmChaStats(username)

        assertThat(result.isSuccess).isTrue()
        val (likes, dislikes) = result.getOrNull()!!
        assertThat(likes).isEqualTo(3)
        assertThat(dislikes).isEqualTo(2)
    }

    @Test
    fun `getUserOsmChaStats handles 401 error gracefully`() = runTest {
        val username = "Antonino_Faro"
        `when`(settingsRepository.getOsmchaTokenOnce()).thenReturn("invalid_token")

        val errorResponse = Response.error<OsmChaResponse>(401, "".toResponseBody())
        `when`(osmChaService.getCheckedChangesets(username, 1, 100)).thenThrow(
            HttpException(
                errorResponse
            )
        )

        val result = repository.getUserOsmChaStats(username)

        assertThat(result.isError).isTrue()
    }

    @Test
    fun `getUserOsmChaStats handles generic exception gracefully`() = runTest {
        val username = "Antonino_Faro"
        `when`(settingsRepository.getOsmchaTokenOnce()).thenReturn("mock_token")

        `when`(
            osmChaService.getCheckedChangesets(
                username,
                1,
                100
            )
        ).thenThrow(RuntimeException("Network error"))

        val result = repository.getUserOsmChaStats(username)

        assertThat(result.isError).isTrue()
    }

    @Test
    fun `verifyTokenDetailed returns Success with username for valid token`() = runTest {
        `when`(osmChaService.getCurrentUser()).thenReturn(OsmChaUserResponse("testuser"))

        val result = repository.verifyTokenDetailed()

        assertThat(result).isInstanceOf(VerifyTokenResult.Success::class.java)
        assertThat((result as VerifyTokenResult.Success).username).isEqualTo("testuser")
    }

    @Test
    fun `verifyTokenDetailed returns Invalid for 401`() = runTest {
        val errorResponse = Response.error<OsmChaUserResponse>(401, "".toResponseBody())
        `when`(osmChaService.getCurrentUser()).thenThrow(HttpException(errorResponse))

        val result = repository.verifyTokenDetailed()

        assertThat(result).isInstanceOf(VerifyTokenResult.Invalid::class.java)
    }

    @Test
    fun `verifyTokenDetailed returns Error for 500 server error`() = runTest {
        val errorResponse = Response.error<OsmChaUserResponse>(500, "".toResponseBody())
        `when`(osmChaService.getCurrentUser()).thenThrow(HttpException(errorResponse))

        val result = repository.verifyTokenDetailed()

        assertThat(result).isInstanceOf(VerifyTokenResult.Error::class.java)
    }

    @Test
    fun `verifyTokenDetailed returns Error for IOException`() = runTest {
        org.mockito.Mockito.doAnswer { throw IOException("Connection refused") }
            .`when`(osmChaService).getCurrentUser()

        val result = repository.verifyTokenDetailed()

        assertThat(result).isInstanceOf(VerifyTokenResult.Error::class.java)
    }
}
