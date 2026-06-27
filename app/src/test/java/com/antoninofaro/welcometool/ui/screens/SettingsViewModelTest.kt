package com.antoninofaro.welcometool.ui.screens

import android.app.Application
import com.antoninofaro.welcometool.data.repository.AppSettings
import com.antoninofaro.welcometool.data.repository.NominatimRepository
import com.antoninofaro.welcometool.data.repository.NotifiedUserStorage
import com.antoninofaro.welcometool.data.repository.OsmChaRepository
import com.antoninofaro.welcometool.data.repository.SettingsRepository
import com.antoninofaro.welcometool.data.repository.VerifyTokenResult
import com.antoninofaro.welcometool.utils.NotificationHelper
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val application = mock<Application>()
    private val settingsRepository = mock<SettingsRepository>()
    private val nominatimRepository = mock<NominatimRepository>()
    private val notifiedUserStorage = mock<NotifiedUserStorage>()
    private val osmChaRepository = mock<OsmChaRepository>()
    private val notificationHelper = mock<NotificationHelper>()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        whenever(settingsRepository.settingsFlow).thenReturn(flowOf(AppSettings()))
        whenever(application.getString(any())).thenReturn("")

        viewModel = SettingsViewModel(
            application = application,
            settingsRepository = settingsRepository,
            nominatimRepository = nominatimRepository,
            notifiedUserStorage = notifiedUserStorage,
            osmChaRepository = osmChaRepository,
            notificationHelper = notificationHelper
        )
    }

    @After
    fun cleanup() {
        Dispatchers.resetMain()
    }

    @Test
    fun `updateOsmchaToken emits Success and saves username on valid token`() = runTest {
        whenever(osmChaRepository.verifyTokenDetailed())
            .thenReturn(VerifyTokenResult.Success("mario_rossi"))

        viewModel.updateOsmchaToken("abcdef1234567890abcdef1234567890abcdef12")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.tokenVerification.value
        assertThat(state).isInstanceOf(TokenVerificationState.Success::class.java)
        assertThat((state as TokenVerificationState.Success).username).isEqualTo("mario_rossi")
        verify(settingsRepository).updateVerifiedOsmchaUsername("mario_rossi")
    }

    @Test
    fun `updateOsmchaToken clears token and emits Error on 401`() = runTest {
        whenever(osmChaRepository.verifyTokenDetailed())
            .thenReturn(VerifyTokenResult.Invalid("Token non valido"))

        viewModel.updateOsmchaToken("abcdef1234567890abcdef1234567890abcdef12")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.tokenVerification.value
        assertThat(state).isInstanceOf(TokenVerificationState.Error::class.java)
        verify(settingsRepository).clearOsmchaToken()
    }

    @Test
    fun `updateOsmchaToken keeps token and emits Error on network failure`() = runTest {
        whenever(osmChaRepository.verifyTokenDetailed())
            .thenReturn(VerifyTokenResult.Error("Connection error"))

        viewModel.updateOsmchaToken("abcdef1234567890abcdef1234567890abcdef12")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.tokenVerification.value
        assertThat(state).isInstanceOf(TokenVerificationState.Error::class.java)
        verify(settingsRepository, never()).clearOsmchaToken()
    }

    @Test
    fun `updateOsmchaToken emits Idle for blank token`() = runTest {
        viewModel.updateOsmchaToken("")
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.tokenVerification.value)
            .isInstanceOf(TokenVerificationState.Idle::class.java)
        verify(osmChaRepository, never()).verifyTokenDetailed()
    }

    @Test
    fun `reverifyOsmchaToken clears token with Idle when silent and 401`() = runTest {
        whenever(settingsRepository.getOsmchaTokenOnce()).thenReturn("saved_token_40_chars_long_0123456789abc")
        whenever(osmChaRepository.verifyTokenDetailed())
            .thenReturn(VerifyTokenResult.Invalid("Token non valido"))

        viewModel.reverifyOsmchaToken(silent = true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.tokenVerification.value)
            .isInstanceOf(TokenVerificationState.Idle::class.java)
        verify(settingsRepository).clearOsmchaToken()
    }

    @Test
    fun `reverifyOsmchaToken clears token with Error when not silent and 401`() = runTest {
        whenever(settingsRepository.getOsmchaTokenOnce()).thenReturn("saved_token_40_chars_long_0123456789abc")
        whenever(osmChaRepository.verifyTokenDetailed())
            .thenReturn(VerifyTokenResult.Invalid("Token non valido"))

        viewModel.reverifyOsmchaToken(silent = false)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.tokenVerification.value)
            .isInstanceOf(TokenVerificationState.Error::class.java)
        verify(settingsRepository).clearOsmchaToken()
    }

    @Test
    fun `reverifyOsmchaToken does nothing when no token saved`() = runTest {
        whenever(settingsRepository.getOsmchaTokenOnce()).thenReturn("")

        viewModel.reverifyOsmchaToken()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.tokenVerification.value)
            .isInstanceOf(TokenVerificationState.Idle::class.java)
        verify(osmChaRepository, never()).verifyTokenDetailed()
    }

    @Test
    fun `clearOsmchaToken clears token and emits Idle`() = runTest {
        viewModel.clearOsmchaToken()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(viewModel.tokenVerification.value)
            .isInstanceOf(TokenVerificationState.Idle::class.java)
        verify(settingsRepository).clearOsmchaToken()
    }
}
