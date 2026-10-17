package com.antoninofaro.welcometool.ui.screens

import android.app.Application
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antoninofaro.welcometool.BuildConfig
import com.antoninofaro.welcometool.R
import com.antoninofaro.welcometool.data.cache.CacheCategory
import com.antoninofaro.welcometool.data.cache.CacheRegistry
import com.antoninofaro.welcometool.data.cache.CacheSizeCalculator
import com.antoninofaro.welcometool.data.cache.CacheSizesResult
import com.antoninofaro.welcometool.data.local.AppDatabase
import com.antoninofaro.welcometool.data.model.Result
import com.antoninofaro.welcometool.data.repository.AppSettings
import com.antoninofaro.welcometool.data.repository.MonitoringArea
import com.antoninofaro.welcometool.data.repository.NominatimRepository
import com.antoninofaro.welcometool.data.repository.NotifiedUserStorage
import com.antoninofaro.welcometool.data.repository.OsmChaRepository
import com.antoninofaro.welcometool.data.repository.SettingsRepository
import com.antoninofaro.welcometool.data.repository.VerifyTokenResult
import com.antoninofaro.welcometool.data.repository.WelcomedUserStorage
import com.antoninofaro.welcometool.utils.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
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
    private val welcomedUserStorage: WelcomedUserStorage,
    private val osmChaRepository: OsmChaRepository,
    private val notificationHelper: NotificationHelper,
    private val cacheRegistry: CacheRegistry,
    private val cacheSizeCalculator: CacheSizeCalculator,
    private val appDatabase: AppDatabase
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

    val cacheCategories: StateFlow<List<CacheCategory>> =
        settingsRepository.settingsFlow
            .map { _ -> cacheRegistry.getCategories() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    private val _cacheSizes = MutableStateFlow<CacheSizesResult?>(null)
    val cacheSizes: StateFlow<CacheSizesResult?> = _cacheSizes.asStateFlow()

    private val _isCalculatingSizes = MutableStateFlow(false)
    val isCalculatingSizes: StateFlow<Boolean> = _isCalculatingSizes.asStateFlow()

    private var cacheSizeJob: Job? = null

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

    fun triggerManualSync(context: android.content.Context) {
        com.antoninofaro.welcometool.utils.WorkerUtils.triggerOneTimeOsmSync(context)
    }


    suspend fun completeOnboarding(area: MonitoringArea) {
        settingsRepository.completeOnboarding(area)
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

    fun updateIndividualChangesetNotifications(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateIndividualChangesetNotifications(enabled)
        }
    }

    fun updateNotificationAutoDismissHours(hours: Int) {
        viewModelScope.launch {
            settingsRepository.updateNotificationAutoDismissHours(hours)
        }
    }

    fun clearCacheCategory(categoryId: String) {
        viewModelScope.launch {
            cacheRegistry.getCategories().firstOrNull { it.id == categoryId }?.clear()
        }
    }

    fun startCacheSizeCalculation() {
        if (_isCalculatingSizes.value) return
        cacheSizeJob?.cancel()
        cacheSizeJob = viewModelScope.launch {
            _isCalculatingSizes.value = true
            try {
                val result = cacheSizeCalculator.calculateAllSizes(cacheRegistry)
                _cacheSizes.value = result
            } catch (e: Exception) {
                // Ignore failure
            } finally {
                _isCalculatingSizes.value = false
            }
        }
    }

    fun cancelCacheSizeCalculation() {
        cacheSizeJob?.cancel()
        _isCalculatingSizes.value = false
        _cacheSizes.value = null
    }

    // Keep this for backward compatibility if needed by the UI
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

    private val _settingsMessage = MutableStateFlow<String?>(null)
    val settingsMessage: StateFlow<String?> = _settingsMessage.asStateFlow()

    fun clearSettingsMessage() {
        _settingsMessage.value = null
    }

    enum class ExportFormat {
        CSV, JSON
    }

    fun exportWelcomedUsers(context: android.content.Context, format: ExportFormat) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val welcomedIds = welcomedUserStorage.getAllWelcomedIds()
                if (welcomedIds.isEmpty()) {
                    _settingsMessage.value = application.getString(R.string.export_empty)
                    return@launch
                }
                val users = appDatabase.userDao().getUsersByIds(welcomedIds.map { it.toLong() }.toSet())
                val usersMap = users.associateBy { it.id }

                val content = when (format) {
                    ExportFormat.JSON -> {
                        val jsonArray = JSONArray()
                        welcomedIds.forEach { idStr ->
                            val userId = idStr.toLongOrNull() ?: 0L
                            val user = usersMap[userId]
                            val obj = JSONObject().apply {
                                put("id", userId)
                                if (user != null) {
                                    put("displayName", user.displayName)
                                    put("accountCreated", user.accountCreated)
                                    put("accountAgeDays", user.accountAge)
                                    put("totalEdits", user.totalEdits)
                                    put("description", user.description)
                                    put("isNewcomer", user.isNewcomer)
                                    put("isReturning", user.isReturning)
                                    put("isPowerUser", user.isPowerUser)
                                    put("isWelcomed", true)
                                    put("osmchaLikes", user.osmchaLikes)
                                    put("osmchaDislikes", user.osmchaDislikes)
                                }
                            }
                            jsonArray.put(obj)
                        }
                        jsonArray.toString(2)
                    }
                    ExportFormat.CSV -> {
                        val sb = StringBuilder()
                        sb.append("id,display_name,account_created,account_age_days,total_edits,is_newcomer,is_returning,is_power_user,osmcha_likes,osmcha_dislikes,description\n")
                        welcomedIds.forEach { idStr ->
                            val userId = idStr.toLongOrNull() ?: 0L
                            val user = usersMap[userId]
                            val displayName = escapeCsv(user?.displayName ?: "")
                            val accountCreated = user?.accountCreated ?: 0L
                            val accountAge = user?.accountAge ?: 0
                            val totalEdits = user?.totalEdits ?: 0
                            val isNewcomer = user?.isNewcomer ?: false
                            val isReturning = user?.isReturning ?: false
                            val isPowerUser = user?.isPowerUser ?: false
                            val likes = user?.osmchaLikes ?: 0
                            val dislikes = user?.osmchaDislikes ?: 0
                            val desc = escapeCsv(user?.description ?: "")
                            sb.append("$userId,$displayName,$accountCreated,$accountAge,$totalEdits,$isNewcomer,$isReturning,$isPowerUser,$likes,$dislikes,$desc\n")
                        }
                        sb.toString()
                    }
                }

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Welcomed Users Export (${format.name})")
                    putExtra(Intent.EXTRA_TEXT, content)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                val chooser = Intent.createChooser(shareIntent, "Export Welcomed Users").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(chooser)
            } catch (e: Exception) {
                _settingsMessage.value = application.getString(R.string.export_error, e.localizedMessage ?: "Unknown error")
            }
        }
    }

    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
        return value
    }

    fun clearAllCaches() {
        viewModelScope.launch {
            cacheRegistry.getCategories().forEach { category ->
                category.clear()
            }
            startCacheSizeCalculation()
        }
    }
}
