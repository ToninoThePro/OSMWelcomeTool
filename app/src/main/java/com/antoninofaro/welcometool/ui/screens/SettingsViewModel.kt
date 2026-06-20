package com.antoninofaro.welcometool.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antoninofaro.welcometool.data.repository.AppSettings
import com.antoninofaro.welcometool.data.repository.MonitoringArea
import com.antoninofaro.welcometool.data.repository.NominatimRepository
import com.antoninofaro.welcometool.data.repository.NotifiedUserStorage
import com.antoninofaro.welcometool.data.repository.SettingsRepository
import com.antoninofaro.welcometool.data.model.Result
import com.antoninofaro.welcometool.utils.LogCaptureTree
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val nominatimRepository: NominatimRepository,
    private val notifiedUserStorage: NotifiedUserStorage,
    private val logCaptureTree: LogCaptureTree
) : ViewModel() {

    private val _nominatimResults = MutableStateFlow<List<MonitoringArea>>(emptyList())
    val nominatimResults: StateFlow<List<MonitoringArea>> = _nominatimResults.asStateFlow()

    private val _isSearchingAreas = MutableStateFlow(false)
    val isSearchingAreas: StateFlow<Boolean> = _isSearchingAreas.asStateFlow()

    private val _areaSearchError = MutableStateFlow<String?>(null)
    val areaSearchError: StateFlow<String?> = _areaSearchError.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    fun updateDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateDarkMode(enabled)
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

    fun searchAreas(query: String) {
        viewModelScope.launch {
            _isSearchingAreas.value = true
            _areaSearchError.value = null

            when (val result = nominatimRepository.searchAreas(query.trim())) {
                is Result.Success -> _nominatimResults.value = result.data
                is Result.Error -> {
                    _nominatimResults.value = emptyList()
                    _areaSearchError.value = result.message ?: "Errore ricerca aree"
                }

                is Result.Loading -> Unit
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


    fun updateOsmchaToken(token: String) {
        viewModelScope.launch {
            settingsRepository.updateOsmchaToken(token)
        }
    }

    fun updateOsmchaChangesetsLimit(limit: Int) {
        viewModelScope.launch {
            settingsRepository.updateOsmchaChangesetsLimit(limit)
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            settingsRepository.resetToDefaults()
        }
    }

    fun updateDebugLogsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateDebugLogsEnabled(enabled)
            logCaptureTree.setEnabled(enabled)
        }
    }

    fun clearNotifiedUsers() {
        viewModelScope.launch {
            notifiedUserStorage.clearAll()
        }
    }
}
