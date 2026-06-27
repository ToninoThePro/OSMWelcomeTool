package com.antoninofaro.welcometool.ui.screens

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antoninofaro.welcometool.BuildConfig
import com.antoninofaro.welcometool.R
import com.antoninofaro.welcometool.data.model.Result
import com.antoninofaro.welcometool.data.repository.AppSettings
import com.antoninofaro.welcometool.data.repository.MonitoringArea
import com.antoninofaro.welcometool.data.repository.NominatimRepository
import com.antoninofaro.welcometool.data.repository.NotifiedUserStorage
import com.antoninofaro.welcometool.data.repository.OsmChaRepository
import com.antoninofaro.welcometool.data.repository.SettingsRepository
import com.antoninofaro.welcometool.data.repository.VerifyTokenResult
import com.antoninofaro.welcometool.utils.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface TokenVerificationState {
    data object Idle : TokenVerificationState
    data object Verifying : TokenVerificationState
    data class Success(val username: String) : TokenVerificationState
    data class Error(val message: String) : TokenVerificationState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application,
    private val settingsRepository: SettingsRepository,
    private val nominatimRepository: NominatimRepository,
    private val notifiedUserStorage: NotifiedUserStorage,
    private val osmChaRepository: OsmChaRepository,
    private val notificationHelper: NotificationHelper
) : ViewModel() {

    private val _nominatimResults = MutableStateFlow<List<MonitoringArea>>(emptyList())
    val nominatimResults: StateFlow<List<MonitoringArea>> = _nominatimResults.asStateFlow()

    private val _isSearchingAreas = MutableStateFlow(false)
    val isSearchingAreas: StateFlow<Boolean> = _isSearchingAreas.asStateFlow()

    private val _areaSearchError = MutableStateFlow<String?>(null)
    val areaSearchError: StateFlow<String?> = _areaSearchError.asStateFlow()

    private val _tokenVerification =
        MutableStateFlow<TokenVerificationState>(TokenVerificationState.Idle)
    val tokenVerification: StateFlow<TokenVerificationState> = _tokenVerification.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    private var verifyJob: Job? = null

    init {
        verifyJob = viewModelScope.launch {
            val s = settingsRepository.settingsFlow.first()
            if (s.osmchaToken.isNotBlank() && s.verifiedOsmchaUsername.isBlank()) {
                verifyWithState(showSnackbarOnInvalid = false)
            }
        }
    }

    fun updateThemeMode(mode: String) {
        viewModelScope.launch {
            settingsRepository.updateThemeMode(mode)
        }
    }

    fun updateDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateDynamicColor(enabled)
        }
    }

    fun updateAutoRefresh(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateAutoRefresh(enabled)
        }
    }

    fun updateAutoRefreshInterval(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.updateAutoRefreshInterval(minutes)
        }
    }

    fun setOnboardingCompleted(completed: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateOnboardingCompleted(completed)
        }
    }

    suspend fun completeOnboarding(area: MonitoringArea) {
        settingsRepository.setDefaultMonitoringArea(area)
        settingsRepository.updateOnboardingCompleted(true)
    }

    fun searchAreas(query: String) {
        viewModelScope.launch {
            _isSearchingAreas.value = true
            _areaSearchError.value = null

            when (val result = nominatimRepository.searchAreas(query.trim())) {
                is Result.Success -> _nominatimResults.value = result.data
                is Result.Error -> {
                    _nominatimResults.value = emptyList()
                    _areaSearchError.value =
                        result.message ?: application.getString(R.string.error_area_search)
                }

            }

            _isSearchingAreas.value = false
        }
    }

    fun clearAreaSearchResults() {
        _nominatimResults.value = emptyList()
        _areaSearchError.value = null
    }

    fun addMonitoringArea(area: MonitoringArea) {
        viewModelScope.launch {
            settingsRepository.addMonitoringArea(area)
        }
    }

    fun removeMonitoringArea(bbox: String) {
        viewModelScope.launch {
            settingsRepository.removeMonitoringArea(bbox)
        }
    }

    fun setDefaultMonitoringArea(area: MonitoringArea) {
        viewModelScope.launch {
            settingsRepository.setDefaultMonitoringArea(area)
        }
    }

    fun updateShowNotifications(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateShowNotifications(enabled)
        }
    }

    fun updateShowNewChangesetNotifications(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateShowNewChangesetNotifications(enabled)
        }
    }

    fun updateMinChangesetsFilter(min: Int) {
        viewModelScope.launch {
            settingsRepository.updateMinChangesetsFilter(min)
        }
    }

    fun updateCacheEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateCacheEnabled(enabled)
        }
    }

    fun sendTestNotification() {
        if (BuildConfig.DEBUG) {
            notificationHelper.sendTestNotification()
        }
    }


    fun updateOsmchaToken(token: String) {
        verifyJob?.cancel()
        verifyJob = viewModelScope.launch {
            settingsRepository.updateOsmchaToken(token)
            if (token.isBlank()) {
                _tokenVerification.value = TokenVerificationState.Idle
                return@launch
            }
            verifyWithState(showSnackbarOnInvalid = true)
        }
    }

    fun reverifyOsmchaToken(silent: Boolean = false) {
        verifyJob?.cancel()
        verifyJob = viewModelScope.launch {
            val savedToken = settingsRepository.getOsmchaTokenOnce()
            if (savedToken.isBlank()) return@launch
            verifyWithState(showSnackbarOnInvalid = !silent)
        }
    }

    private suspend fun verifyWithState(showSnackbarOnInvalid: Boolean) {
        _tokenVerification.value = TokenVerificationState.Verifying
        _tokenVerification.value = when (val result = osmChaRepository.verifyTokenDetailed()) {
            is VerifyTokenResult.Success -> {
                settingsRepository.updateVerifiedOsmchaUsername(result.username)
                TokenVerificationState.Success(result.username)
            }

            is VerifyTokenResult.Invalid -> {
                settingsRepository.clearOsmchaToken()
                if (showSnackbarOnInvalid) {
                    TokenVerificationState.Error(result.message)
                } else {
                    TokenVerificationState.Idle
                }
            }

            is VerifyTokenResult.Error -> {
                TokenVerificationState.Error(
                    result.message.ifBlank { application.getString(R.string.token_verify_error) }
                )
            }
        }
    }

    fun clearOsmchaToken() {
        viewModelScope.launch {
            settingsRepository.clearOsmchaToken()
            _tokenVerification.value = TokenVerificationState.Idle
        }
    }

    fun updateOsmchaChangesetsLimit(limit: Int) {
        viewModelScope.launch {
            settingsRepository.updateOsmchaChangesetsLimit(limit)
        }
    }

    fun updateOsmchaAutoRefreshDays(days: Int) {
        viewModelScope.launch {
            settingsRepository.updateOsmchaAutoRefreshDays(days)
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            settingsRepository.resetToDefaults()
        }
    }

    fun clearNotifiedUsers() {
        viewModelScope.launch {
            notifiedUserStorage.clearAll()
        }
    }

    fun getTokenVerifiedMessage(username: String): String =
        application.getString(R.string.token_verified_snackbar, username)
}
