package com.antoninofaro.welcometool.ui.screens

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antoninofaro.welcometool.R
import com.antoninofaro.welcometool.data.entity.UserAreaActivityEntity
import com.antoninofaro.welcometool.data.entity.UserEntity
import com.antoninofaro.welcometool.data.local.dao.UserDao
import com.antoninofaro.welcometool.data.local.model.UserAreaActivityWithUser
import com.antoninofaro.welcometool.data.model.CountWrapper
import com.antoninofaro.welcometool.data.model.OsmChangeset
import com.antoninofaro.welcometool.data.model.OsmUser
import com.antoninofaro.welcometool.data.model.Result
import com.antoninofaro.welcometool.data.model.UserImage
import com.antoninofaro.welcometool.data.repository.OsmRepository
import com.antoninofaro.welcometool.data.repository.SettingsRepository
import com.antoninofaro.welcometool.data.repository.WelcomedUserStorage
import com.antoninofaro.welcometool.domain.UserAnalysis
import com.antoninofaro.welcometool.domain.UserAnalyzer
import com.antoninofaro.welcometool.utils.ConnectivityObserver
import com.antoninofaro.welcometool.utils.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject

data class UserUiModel(
    val user: OsmUser,
    val analysis: UserAnalysis,
    val lastUpdated: Long = 0L,
    val osmchaLastChecked: Long = 0L,
)

enum class OsmchaState { NoToken, Loading, Loaded, Error }

data class MainUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isPaging: Boolean = false,
    val users: List<UserUiModel> = emptyList(),
    val filteredUsers: List<UserUiModel> = emptyList(),
    val errorMessage: String? = null,
    val hasReachedEnd: Boolean = false,
    val selectedBBox: String = Constants.ITALY_BBOX,
    val searchTerm: String = "",
    val minChanges: Int = 0,
    val filterIsNewcomer: Boolean = false,
    val filterIsReturning: Boolean = false,
    val filterIsPowerUser: Boolean = false,
    val osmchaState: OsmchaState = OsmchaState.NoToken,
    val osmchaLikes: Int = 0,
    val osmchaDislikes: Int = 0,
    val isOnline: Boolean = true,
    val lastSyncTimestamp: Long = 0L,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val application: Application,
    private val repository: OsmRepository,
    private val welcomedUserStorage: WelcomedUserStorage,
    private val osmChaRepository: com.antoninofaro.welcometool.data.repository.OsmChaRepository,
    private val userDao: UserDao,
    private val settingsRepository: SettingsRepository,
    private val connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    companion object {
        private const val USER_CACHE_TTL_MS = 24 * 60 * 60 * 1000L
        private const val CACHE_PAGE_TTL_MS = 30 * 60 * 1000L
        private const val DORMANT_USER_THRESHOLD_MS = 15L * 24 * 60 * 60 * 1000L // 15 Days
        private const val LOCAL_PAGE_SIZE = 100
        private const val INITIAL_SCAN_WINDOWS = 8
        private const val MAX_RECENT_CHANGESETS = 100
        private const val BATCH_FETCH_SIZE = 50
        private const val OSM_EPOCH_START = "2005-01-01T00:00:00Z"
    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private fun updateUiState(transform: (MainUiState) -> MainUiState) {
        _uiState.value = transform(_uiState.value)
    }


    private val welcomedMutex = Mutex()
    private val toggleMutex = Mutex()
    private var welcomedIdsCache: Set<String>? = null
    private var oldestChangesetCursor: String? = null
    private var currentPageOffset: Int = 0

    init {
        viewModelScope.launch {
            val savedBBox = settingsRepository.settingsFlow.first().defaultBBox
            if (savedBBox != Constants.ITALY_BBOX) {
                _uiState.value = _uiState.value.copy(selectedBBox = savedBBox)
            }
            loadData(forceRefresh = true)
        }
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _uiState.value = _uiState.value.copy(isOnline = online)
            }
        }
    }

    private data class ProcessedUser(
        val uiModel: UserUiModel,
        val entityToPersist: UserEntity?,
        val areaActivityToPersist: UserAreaActivityEntity?
    )

    fun loadData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            currentPageOffset = 0
            oldestChangesetCursor = null
            val selectedBBox = _uiState.value.selectedBBox
            val showRefreshIndicator = forceRefresh && _uiState.value.users.isNotEmpty()

            _uiState.value = _uiState.value.copy(
                isLoading = !showRefreshIndicator,
                isRefreshing = showRefreshIndicator,
                isPaging = false,
                errorMessage = null,
                hasReachedEnd = false
            )

            try {
                val pageUsers = loadLocalUsersPage(selectedBBox, currentPageOffset)

                // If not force refresh, check if page is stale based on autoRefreshInterval
                if (pageUsers.isNotEmpty() && !forceRefresh) {
                    val newestLastUpdated = pageUsers.maxOfOrNull { it.lastUpdated } ?: 0L
                    val refreshIntervalMs = settings.autoRefreshInterval * 60 * 1000L
                    val isPageFresh =
                        (System.currentTimeMillis() - newestLastUpdated) < refreshIntervalMs

                    if (isPageFresh) {
                        _uiState.value = _uiState.value.copy(
                            users = pageUsers,
                            filteredUsers = pageUsers,
                            hasReachedEnd = pageUsers.size < LOCAL_PAGE_SIZE
                        )
                        applyFilters()
                        return@launch
                    }
                }

                syncRecentUsersForCurrentBBox(selectedBBox)

                _uiState.value = _uiState.value.copy(lastSyncTimestamp = System.currentTimeMillis())

                val refreshedUsers = loadLocalUsersPage(selectedBBox, currentPageOffset)

                if (refreshedUsers.isEmpty()) {
                    loadCachedUsersOrShowError(
                        selectedBBox,
                        application.getString(R.string.no_users_loaded)
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    users = refreshedUsers,
                    filteredUsers = refreshedUsers,
                    hasReachedEnd = refreshedUsers.size < LOCAL_PAGE_SIZE
                )
                applyFilters()

                Timber.d(
                    "Loaded ${refreshedUsers.size} utenti locali per bbox $selectedBBox, offset=$currentPageOffset, next cursor=$oldestChangesetCursor"
                )

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Error loading data")
                loadCachedUsersOrShowError(
                    selectedBBox,
                    application.getString(R.string.error_prefix, e.message)
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false, isRefreshing = false)
            }
        }
    }

    private suspend fun syncRecentUsersForCurrentBBox(bbox: String) {
        val welcomedIds = getWelcomedIds()
        var scanCursor = oldestChangesetCursor
        val seenUserIds = mutableSetOf<Long>()
        val allProcessedUsers = mutableListOf<ProcessedUser>()
        val latestChangesetByUid = mutableMapOf<Long, OsmChangeset>()
        val staleUids = mutableListOf<Long>()

        for (window in 0 until INITIAL_SCAN_WINDOWS) {
            val changesetsResult = repository.fetchRecentChangesets(
                bbox,
                buildTimeRange(scanCursor),
                MAX_RECENT_CHANGESETS
            )

            if (changesetsResult !is Result.Success) break
            val changesets = changesetsResult.data
            if (changesets.isEmpty()) break

            changesets.reversed().forEach { latestChangesetByUid.putIfAbsent(it.uid, it) }

            val currentWindowUids = changesets.map { it.uid }.distinct()
            val uidsToSync = currentWindowUids.filterNot { seenUserIds.contains(it) }

            if (uidsToSync.isNotEmpty()) {
                val cachedUsersMap = userDao.getUsersByIds(uidsToSync).associateBy { it.id }

                uidsToSync.forEach { uid ->
                    val cached = cachedUsersMap[uid]
                    val recent = latestChangesetByUid[uid] ?: return@forEach
                    if (!isUserUpdateRequired(cached, recent)) {
                        val analysis = (cached ?: return@forEach).toAnalysis(recent.createdAt)
                            .copy(isWelcomed = welcomedIds.contains(uid.toString()))

                        allProcessedUsers.add(
                            ProcessedUser(
                                uiModel = UserUiModel(
                                    cached.toOsmUser(),
                                    analysis,
                                    cached.lastUpdated,
                                    cached.osmchaLastChecked
                                ),
                                entityToPersist = null,
                                areaActivityToPersist = UserAreaActivityEntity(
                                    bbox,
                                    uid,
                                    recent.createdAt,
                                    recent.id
                                )
                            )
                        )
                        seenUserIds.add(uid)
                    } else {
                        staleUids.add(uid)
                    }
                }
            }

            val nextCursor = computeOlderCursor(changesets)
            if (nextCursor == null || nextCursor == scanCursor) break
            oldestChangesetCursor = nextCursor
            scanCursor = nextCursor
        }

        staleUids.chunked(BATCH_FETCH_SIZE).forEach { batchIds ->
            val remoteUsers = repository.fetchUsersDetails(batchIds).getOrNull()
            if (remoteUsers != null) {
                remoteUsers.forEach { user ->
                    val recent = latestChangesetByUid[user.id] ?: return@forEach
                    val isWelcomed = welcomedIds.contains(user.id.toString())
                    val analysis =
                        UserAnalyzer.analyze(user, emptyList(), recent, isWelcomed = isWelcomed)
                    val userEntity = user.toEntity(analysis)

                    allProcessedUsers.add(
                        ProcessedUser(
                            uiModel = UserUiModel(
                                user,
                                analysis,
                                userEntity.lastUpdated,
                                userEntity.osmchaLastChecked
                            ),
                            entityToPersist = userEntity,
                            areaActivityToPersist = UserAreaActivityEntity(
                                bbox,
                                user.id,
                                recent.createdAt,
                                recent.id
                            )
                        )
                    )
                    seenUserIds.add(user.id)
                }
            }
        }

        val entities = allProcessedUsers.mapNotNull { it.entityToPersist }
        val activities = allProcessedUsers.mapNotNull { it.areaActivityToPersist }
        if (entities.isNotEmpty()) userDao.insertUsers(entities)
        if (activities.isNotEmpty()) userDao.insertUserAreaActivities(activities)
    }

    private suspend fun loadLocalUsersPage(bbox: String, offset: Int): List<UserUiModel> {
        return userDao.getUsersForBBoxPage(bbox, LOCAL_PAGE_SIZE, offset)
            .map { it.toUiModel() }
            .let(::sortUsersByAreaActivity)
    }

    private fun buildTimeRange(cursorEnd: String?): String? {
        return cursorEnd?.let { "$OSM_EPOCH_START,$it" }
    }

    private fun computeOlderCursor(changesets: List<OsmChangeset>): String? {
        return changesets.minByOrNull { it.createdAt }?.createdAt
    }

    private suspend fun loadCachedUsersOrShowError(bbox: String, fallbackErrorMessage: String) {
        val cachedUsers = loadLocalUsersPage(bbox, currentPageOffset)
        if (cachedUsers.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                users = cachedUsers,
                filteredUsers = cachedUsers,
                errorMessage = null,
                isLoading = false,
                hasReachedEnd = cachedUsers.size < LOCAL_PAGE_SIZE
            )
            applyFilters()
        } else {
            _uiState.value = _uiState.value.copy(
                errorMessage = fallbackErrorMessage,
                isLoading = false
            )
        }
    }

    private fun sortUsersByAreaActivity(users: List<UserUiModel>): List<UserUiModel> {
        return users.sortedWith(
            compareByDescending<UserUiModel> { it.analysis.lastActiveDate ?: "" }
                .thenByDescending { it.lastUpdated }
        )
    }

    private fun isUserUpdateRequired(cached: UserEntity?, recent: OsmChangeset): Boolean {
        if (cached == null) return true
        if (recent.createdAt > (cached.lastActiveDate ?: "")) return true

        val isStale = System.currentTimeMillis() - cached.lastUpdated > USER_CACHE_TTL_MS
        val isDormant = cached.lastActiveDate?.let { lastDate ->
            try {
                val lastMillis = java.time.Instant.parse(lastDate).toEpochMilli()
                (System.currentTimeMillis() - lastMillis) > DORMANT_USER_THRESHOLD_MS
            } catch (e: Exception) {
                false
            }
        } ?: false

        return isStale && !isDormant
    }

    private fun isPageStale(pageUsers: List<UserUiModel>): Boolean {
        val newestLastUpdated = pageUsers.maxOfOrNull { it.lastUpdated } ?: return true
        return System.currentTimeMillis() - newestLastUpdated > CACHE_PAGE_TTL_MS
    }

    private suspend fun getWelcomedIds(forceRefresh: Boolean = false): Set<String> {
        return welcomedMutex.withLock {
            if (!forceRefresh) {
                welcomedIdsCache?.let { return@withLock it }
            }
            welcomedUserStorage.getAllWelcomedIds().also { welcomedIdsCache = it }
        }
    }

    private fun UserEntity.toUiModel(): UserUiModel {
        val osmUser = OsmUser(
            id = this.id,
            displayName = this.displayName,
            accountCreated = this.accountCreated,
            description = this.description,
            img = this.imgUrl?.let { UserImage(it) },
            changesets = CountWrapper(this.totalEdits),
            roles = emptyList(),
            traces = null
        )

        val analysis = UserAnalysis(
            accountAge = this.accountAge,
            isNewcomer = this.isNewcomer,
            isReturning = this.isReturning,
            isPowerUser = this.isPowerUser,
            totalEdits = this.totalEdits,
            firstChangesetDate = this.firstChangesetDate,
            lastActiveDate = this.lastActiveDate,
            osmchaLikes = this.osmchaLikes,
            osmchaDislikes = this.osmchaDislikes,
            isWelcomed = this.isWelcomed
        )
        return UserUiModel(osmUser, analysis, this.lastUpdated, this.osmchaLastChecked)
    }

    fun loadOsmchaForUser(userId: Long) {
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            if (settings.osmchaToken.isBlank()) {
                _uiState.value = _uiState.value.copy(osmchaState = OsmchaState.NoToken)
                return@launch
            }

            val current = _uiState.value.users.find { it.user.id == userId } ?: return@launch
            val lastChecked = current.osmchaLastChecked
            val refreshIntervalMs = settings.osmchaAutoRefreshDays * 86_400_000L
            val isFresh =
                lastChecked > 0 && (System.currentTimeMillis() - lastChecked) <= refreshIntervalMs

            if (isFresh) {
                // If data is fresh, ensure UI reflects it and set state to Loaded
                _uiState.value = _uiState.value.copy(
                    osmchaState = OsmchaState.Loaded,
                    osmchaLikes = current.analysis.osmchaLikes,
                    osmchaDislikes = current.analysis.osmchaDislikes
                )
                return@launch
            }

            if (lastChecked > 0) {
                _uiState.value = _uiState.value.copy(osmchaState = OsmchaState.Loading)
            } else {
                _uiState.value = _uiState.value.copy(
                    osmchaState = OsmchaState.Loading,
                    osmchaLikes = 0,
                    osmchaDislikes = 0
                )
            }
            doFetchOsmcha(userId)
        }
    }

    fun refreshOsmchaForUser(userId: Long) {
        viewModelScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            if (settings.osmchaToken.isBlank()) {
                _uiState.value = _uiState.value.copy(osmchaState = OsmchaState.NoToken)
                return@launch
            }
            _uiState.value = _uiState.value.copy(osmchaState = OsmchaState.Loading)
            doFetchOsmcha(userId)
        }
    }

    private suspend fun doFetchOsmcha(userId: Long) {
        val currentUser = _uiState.value.users.find { it.user.id == userId } ?: return
        when (val result = osmChaRepository.getUserOsmChaStats(currentUser.user.displayName)) {
            is Result.Success -> {
                val (likes, dislikes) = result.data
                val now = System.currentTimeMillis()
                val updatedUsers = _uiState.value.users.map { userModel ->
                    if (userModel.user.id == userId) userModel.copy(
                        analysis = userModel.analysis.copy(
                            osmchaLikes = likes,
                            osmchaDislikes = dislikes
                        ),
                        osmchaLastChecked = now
                    ) else userModel
                }
                _uiState.value = _uiState.value.copy(
                    osmchaState = OsmchaState.Loaded,
                    osmchaLikes = likes,
                    osmchaDislikes = dislikes,
                    users = updatedUsers
                )
                applyFilters()
                userDao.updateOsmchaStats(userId, likes, dislikes, now, now)
            }

            is Result.Error -> {
                _uiState.value = _uiState.value.copy(osmchaState = OsmchaState.Error)
            }
        }
    }

    fun updateSearchTerm(term: String) {
        _uiState.value = _uiState.value.copy(searchTerm = term)
        applyFilters()
    }

    fun performSearch(query: String) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            _uiState.value =
                _uiState.value.copy(errorMessage = application.getString(R.string.enter_valid_username))
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                val escapedQuery = normalizedQuery
                    .replace("\\", "\\\\")
                    .replace("_", "\\_")
                    .replace("%", "\\%")
                val searchResults = userDao.searchUsersForBBox(
                    _uiState.value.selectedBBox,
                    "%$escapedQuery%"
                ).map { it.toUiModel() }

                if (searchResults.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = application.getString(R.string.user_not_in_cache)
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    filteredUsers = searchResults,
                    isLoading = false,
                    searchTerm = normalizedQuery,
                    filterIsNewcomer = false,
                    filterIsReturning = false,
                    filterIsPowerUser = false
                )


            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Error searching user manually: $normalizedQuery")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = application.getString(R.string.error_search_format, e.message)
                )
            }
        }
    }

    fun loadMoreUsers() {
        if (_uiState.value.isPaging || _uiState.value.isLoading) return
        if (_uiState.value.hasReachedEnd) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPaging = true, errorMessage = null)

            try {
                val nextOffset = currentPageOffset + LOCAL_PAGE_SIZE
                val nextPage = loadLocalUsersPage(_uiState.value.selectedBBox, nextOffset)

                if (nextPage.isEmpty()) {
                    _uiState.value = _uiState.value.copy(hasReachedEnd = true)
                    return@launch
                }

                val mergedUsers = _uiState.value.users + nextPage

                currentPageOffset = nextOffset

                _uiState.value = _uiState.value.copy(
                    users = mergedUsers,
                    hasReachedEnd = nextPage.size < LOCAL_PAGE_SIZE
                )
                applyFilters()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Error loading more users")
                _uiState.value = _uiState.value.copy(
                    errorMessage = application.getString(R.string.error_loading_format, e.message)
                )
            } finally {
                _uiState.value = _uiState.value.copy(isPaging = false)
            }
        }
    }

    fun updateBBox(bbox: String) {
        if (_uiState.value.selectedBBox != bbox) {
            currentPageOffset = 0
            _uiState.value = _uiState.value.copy(selectedBBox = bbox, hasReachedEnd = false)
            oldestChangesetCursor = null
            loadData(forceRefresh = true)
        }
    }

    fun onPullToRefresh() {
        loadData(forceRefresh = true)
    }

    fun updateMinChanges(min: Int) {
        _uiState.value = _uiState.value.copy(minChanges = min.coerceAtLeast(0))
        applyFilters()
    }

    fun toggleNewcomerFilter() {
        _uiState.value = _uiState.value.copy(filterIsNewcomer = !_uiState.value.filterIsNewcomer)
        applyFilters()
    }

    fun toggleReturningFilter() {
        _uiState.value = _uiState.value.copy(filterIsReturning = !_uiState.value.filterIsReturning)
        applyFilters()
    }

    fun togglePowerUserFilter() {
        _uiState.value = _uiState.value.copy(filterIsPowerUser = !_uiState.value.filterIsPowerUser)
        applyFilters()
    }

    private fun UserEntity.toOsmUser(): OsmUser {
        return OsmUser(
            id = id,
            displayName = displayName,
            accountCreated = accountCreated,
            description = description,
            img = imgUrl?.let { UserImage(it) },
            roles = emptyList(),
            changesets = CountWrapper(totalEdits),
            traces = null
        )
    }

    private fun UserEntity.toAnalysis(lastActiveDateOverride: String? = lastActiveDate): UserAnalysis {
        return UserAnalysis(
            accountAge = accountAge,
            isNewcomer = isNewcomer,
            isReturning = isReturning,
            isPowerUser = isPowerUser,
            totalEdits = totalEdits,
            firstChangesetDate = firstChangesetDate,
            lastActiveDate = lastActiveDateOverride,
            osmchaLikes = osmchaLikes,
            osmchaDislikes = osmchaDislikes,
            isWelcomed = isWelcomed
        )
    }

    private fun UserAreaActivityWithUser.toUiModel(): UserUiModel {
        val analysis = user.toAnalysis(lastActiveDateOverride = lastChangesetDate)
        return UserUiModel(
            user = user.toOsmUser(),
            analysis = analysis,
            lastUpdated = areaLastUpdated,
            osmchaLastChecked = user.osmchaLastChecked
        )
    }

    private fun applyFilters() {
        val current = _uiState.value
        val term = current.searchTerm.trim()
        val hasCatFilter =
            current.filterIsNewcomer || current.filterIsReturning || current.filterIsPowerUser

        val filtered = current.users.filter { m ->
            val matchesSearch =
                term.isBlank() || m.user.displayName.contains(term, ignoreCase = true)
            val matchesCategory = !hasCatFilter || (
                    (current.filterIsNewcomer && m.analysis.isNewcomer) ||
                            (current.filterIsReturning && m.analysis.isReturning) ||
                            (current.filterIsPowerUser && m.analysis.isPowerUser)
                    )
            val matchesMinChanges =
                current.minChanges <= 0 || m.analysis.totalEdits >= current.minChanges

            matchesSearch && matchesCategory && matchesMinChanges
        }
        updateUiState { it.copy(filteredUsers = filtered) }
    }

    fun toggleWelcomed(userId: Long, currentStatus: Boolean) {
        viewModelScope.launch {
            toggleMutex.withLock {
                welcomedUserStorage.setWelcomed(userId, !currentStatus)
                userDao.updateWelcomedStatus(userId, !currentStatus, System.currentTimeMillis())
                welcomedMutex.withLock {
                    welcomedIdsCache = welcomedIdsCache?.let {
                        if (!currentStatus) it + userId.toString() else it - userId.toString()
                    }
                }

                val updatedUsers = _uiState.value.users.map { userModel ->
                    if (userModel.user.id == userId) {
                        userModel.copy(
                            analysis = userModel.analysis.copy(isWelcomed = !currentStatus)
                        )
                    } else {
                        userModel
                    }
                }

                _uiState.value = _uiState.value.copy(users = updatedUsers)
                applyFilters()
            }
        }
    }

    fun refreshUser(userId: Long, force: Boolean = false) {
        viewModelScope.launch {
            val cachedUser = userDao.getUserById(userId)

            // Auto-refresh logic: only if forced OR (not stale according to 24h threshold)
            // Note: USER_CACHE_TTL_MS is 24h
            val isFresh =
                cachedUser != null && (System.currentTimeMillis() - cachedUser.lastUpdated) < USER_CACHE_TTL_MS

            if (!force && isFresh) {
                Timber.d("User $userId is fresh (< 24h), skipping auto-refresh")
                return@launch
            }

            val userResult = repository.fetchUserDetail(userId)
            if (userResult !is Result.Success) {
                Timber.w("Failed to refresh user $userId")
                return@launch
            }

            val user = userResult.data

            val welcomedIds = getWelcomedIds()

            val userChangesets = repository.fetchUserChangesets(userId, 100).getOrNull().orEmpty()

            val isWelcomed = welcomedIds.contains(userId.toString())
            val analysis = UserAnalyzer.analyze(user, userChangesets, null, isWelcomed = isWelcomed)

            val userEntity = user.toEntity(analysis)

            userDao.insertUser(userEntity)

            val updatedModel = userEntity.toUiModel()

            _uiState.value = _uiState.value.copy(
                users = _uiState.value.users.map { if (it.user.id == userId) updatedModel else it }
            )
            applyFilters()
        }
    }

    private fun OsmUser.toEntity(analysis: UserAnalysis): UserEntity {
        return UserEntity(
            id = id,
            displayName = displayName,
            accountCreated = accountCreated,
            description = description,
            accountAge = analysis.accountAge,
            isNewcomer = analysis.isNewcomer,
            isReturning = analysis.isReturning,
            isPowerUser = analysis.isPowerUser,
            totalEdits = analysis.totalEdits,
            firstChangesetDate = analysis.firstChangesetDate,
            lastActiveDate = analysis.lastActiveDate,
            osmchaLikes = analysis.osmchaLikes,
            osmchaDislikes = analysis.osmchaDislikes,
            isWelcomed = analysis.isWelcomed,
            imgUrl = img?.href
        )
    }
}
