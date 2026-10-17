package com.antoninofaro.welcometool.ui.screens

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antoninofaro.welcometool.R
import com.antoninofaro.welcometool.data.entity.UserAreaActivityEntity
import com.antoninofaro.welcometool.data.entity.UserEntity
import com.antoninofaro.welcometool.data.local.dao.UserDao
import com.antoninofaro.welcometool.data.model.CountWrapper
import com.antoninofaro.welcometool.data.model.OsmChangeset
import com.antoninofaro.welcometool.data.model.OsmUser
import com.antoninofaro.welcometool.data.model.Result
import com.antoninofaro.welcometool.data.model.UserImage
import com.antoninofaro.welcometool.data.repository.AppSettings
import com.antoninofaro.welcometool.data.repository.OsmRepository
import com.antoninofaro.welcometool.data.repository.SettingsRepository
import com.antoninofaro.welcometool.data.repository.WelcomedUserStorage
import com.antoninofaro.welcometool.domain.UserAnalysis
import com.antoninofaro.welcometool.domain.UserAnalyzer
import com.antoninofaro.welcometool.utils.ConnectivityObserver
import com.antoninofaro.welcometool.utils.Constants
import com.antoninofaro.welcometool.utils.intersectsBBox
import com.antoninofaro.welcometool.utils.toAnalysis
import com.antoninofaro.welcometool.utils.toEntity
import com.antoninofaro.welcometool.utils.toOsmUser
import com.antoninofaro.welcometool.utils.toUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    val errorMessage: String? = null,
    val hasReachedEnd: Boolean = false,
    val selectedBBox: String = Constants.WORLD_BBOX,
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
    private val syncManager: com.antoninofaro.welcometool.domain.ChangesetSyncManager,
    private val areaSyncStateDao: com.antoninofaro.welcometool.data.local.dao.AreaSyncStateDao,
    private val welcomedUserStorage: WelcomedUserStorage,
    private val osmChaRepository: com.antoninofaro.welcometool.data.repository.OsmChaRepository,
    private val userDao: UserDao,
    private val changesetDao: com.antoninofaro.welcometool.data.local.dao.ChangesetDao,
    private val settingsRepository: SettingsRepository,
    private val connectivityObserver: ConnectivityObserver,
) : ViewModel() {

    companion object {
        private const val USER_CACHE_TTL_MS = 24 * 60 * 60 * 1000L
        private const val DORMANT_USER_THRESHOLD_MS = 15L * 24 * 60 * 60 * 1000L // 15 Days
        private const val LOCAL_PAGE_SIZE = 100
        private const val BATCH_FETCH_SIZE = 50
    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val userPagingData: Flow<PagingData<UserUiModel>> = _uiState
        .flatMapLatest { state ->
            val term = state.searchTerm.trim()
                .replace("\\", "\\\\")
                .replace("_", "\\_")
                .replace("%", "\\%")
            Pager(
                config = PagingConfig(
                    pageSize = LOCAL_PAGE_SIZE,
                    enablePlaceholders = false
                ),
                pagingSourceFactory = {
                    userDao.getUsersForBBoxPagingSource(
                        bbox = state.selectedBBox,
                        searchTerm = if (term.isBlank()) "%" else "%$term%",
                        minEdits = state.minChanges,
                        filterNewcomer = state.filterIsNewcomer,
                        filterReturning = state.filterIsReturning,
                        filterPowerUser = state.filterIsPowerUser
                    )
                }
            ).flow
        }
        .map { pagingData ->
            pagingData.map { it.toUiModel() }
        }
        .cachedIn(viewModelScope)

    suspend fun getRecentChangesetsForUser(uid: Long, limit: Int = 3): List<com.antoninofaro.welcometool.data.model.ChangesetUiModel> {
        val entities = changesetDao.getChangesetsForBBoxFiltered(
            bbox = _uiState.value.selectedBBox,
            searchTerm = "",
            filterReviewRequested = false,
            filterAnomalies = false,
            limit = 100, // get more to filter in memory since we filter by uid
            offset = 0
        ).filter { it.uid == uid }.take(limit)

        return entities.map { it.toUiModel(_uiState.value.isOnline) }
    }



    private val welcomedMutex = Mutex()
    private val toggleMutex = Mutex()
    private var welcomedIdsCache: Set<String>? = null
    private var currentPageOffset: Int = 0
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val savedBBox = settingsRepository.settingsFlow.first { it.isLoaded }.defaultBBox
            if (_uiState.value.selectedBBox != savedBBox) {
                _uiState.value = _uiState.value.copy(selectedBBox = savedBBox)
            }
            loadData(forceRefresh = false)
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

                if (pageUsers.isEmpty() && !forceRefresh) {
                    _uiState.value = _uiState.value.copy(
                        users = emptyList(),
                        hasReachedEnd = true
                    )
                    return@launch
                }

                // If not force refresh, check if page is stale based on autoRefreshInterval
                if (pageUsers.isNotEmpty() && !forceRefresh) {
                    val newestLastUpdated = pageUsers.maxOfOrNull { it.lastUpdated } ?: 0L
                    val refreshIntervalMs = settings.autoRefreshInterval * 60 * 1000L
                    val isPageFresh =
                        (System.currentTimeMillis() - newestLastUpdated) < refreshIntervalMs

                    if (isPageFresh) {
                        _uiState.value = _uiState.value.copy(
                            users = pageUsers,
                            hasReachedEnd = pageUsers.size < LOCAL_PAGE_SIZE
                        )
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
                    hasReachedEnd = refreshedUsers.size < LOCAL_PAGE_SIZE
                )

                Timber.d(
                    "Loaded ${refreshedUsers.size} utenti locali per bbox $selectedBBox, offset=$currentPageOffset"
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
        val phaseAResult = syncManager.executePhaseA(bbox = bbox)
        syncManager.executePhaseB(bbox = bbox)
        
        val newChangesets = phaseAResult.getOrNull() ?: emptyList()
        if (newChangesets.isNotEmpty()) {
            processUsersFromChangesets(bbox, newChangesets)
        }
        
        fetchNextUsersWindowFromNetwork(bbox)
    }

    private suspend fun fetchNextUsersWindowFromNetwork(bbox: String): Int {
        var syncState = areaSyncStateDao.getSyncState(bbox) ?: com.antoninofaro.welcometool.data.entity.AreaSyncStateEntity(bbox)
        if (syncState.isHistoricalExhausted) {
            Timber.d("Storico esaurito per quest'area, non lancio nuove chiamate a ritroso.")
            return 0
        }

        var cursor = syncState.backwardCursorDate
        if (cursor == null) {
            cursor = changesetDao.getOldestChangesetDateForBBox(bbox)
        }
        if (cursor == null) {
            cursor = java.time.Instant.now().toString()
        }

        val timeRange = buildTimeRange(cursor)
        val changesetsResult = repository.fetchRecentChangesets(
            bbox = bbox,
            timeRange = timeRange,
            limit = 100
        )

        if (changesetsResult !is Result.Success) return 0
        val rawChangesets = changesetsResult.data
        if (rawChangesets.size < 100) {
            syncState = syncState.copy(isHistoricalExhausted = true)
        }

        if (rawChangesets.isNotEmpty()) {
            val oldestInRaw = rawChangesets.minByOrNull { 
                try { java.time.Instant.parse(it.createdAt).toEpochMilli() } catch (e: Exception) { Long.MAX_VALUE }
            }?.createdAt

            if (oldestInRaw != null) {
                val oldestInstant = java.time.Instant.parse(oldestInRaw)
                val prevCursorInstant = java.time.Instant.parse(cursor)
                
                var newCursorInstant = oldestInstant.plusSeconds(1)
                if (!newCursorInstant.isBefore(prevCursorInstant)) {
                    newCursorInstant = oldestInstant
                    if (!newCursorInstant.isBefore(prevCursorInstant)) {
                        Timber.w("Backward sync cursor stopped progressing for bbox %s. prev=%s, oldest=%s", bbox, cursor, oldestInRaw)
                        syncState = syncState.copy(isHistoricalExhausted = true)
                    } else {
                        syncState = syncState.copy(backwardCursorDate = newCursorInstant.toString())
                    }
                } else {
                    syncState = syncState.copy(backwardCursorDate = newCursorInstant.toString())
                }
            }
        } else {
            syncState = syncState.copy(isHistoricalExhausted = true)
        }

        areaSyncStateDao.insertOrUpdate(syncState)

        if (rawChangesets.isEmpty()) return 0

        val changesets = rawChangesets.filter { it.intersectsBBox(bbox) }

        if (changesets.isNotEmpty()) {
            changesetDao.insertOrUpdateChangesets(changesets.map {
                val tagsJson = if (!it.tags.isNullOrEmpty()) {
                    kotlinx.serialization.json.Json.encodeToString(it.tags)
                } else null
                com.antoninofaro.welcometool.data.entity.ChangesetEntity(
                    id = it.id,
                    bbox = bbox,
                    createdAt = it.createdAt,
                    closedAt = it.closedAt,
                    open = it.open,
                    user = it.user,
                    uid = it.uid,
                    minLat = it.minLat,
                    minLon = it.minLon,
                    maxLat = it.maxLat,
                    maxLon = it.maxLon,
                    numChanges = it.totalChanges,
                    commentsCount = it.commentsCount,
                    tagsJson = tagsJson,
                    fetchSource = "history"
                )
            })
        }

        return processUsersFromChangesets(bbox, changesets)
    }

    private suspend fun processUsersFromChangesets(bbox: String, changesets: List<OsmChangeset>): Int {
        if (changesets.isEmpty()) return 0

        // changesets is sorted descending (newest first). 
        // reversed() makes it ascending (oldest first).
        // associateBy overwrites duplicates, so the map retains the last encountered (which is the newest).
        val latestChangesetByUid = changesets.reversed().associateBy { it.uid }

        val uidsToSync = latestChangesetByUid.keys
        val welcomedIds = getWelcomedIds()
        val allProcessedUsers = mutableListOf<ProcessedUser>()
        val staleUids = mutableListOf<Long>()

        if (uidsToSync.isNotEmpty()) {
            val cacheEnabled = settings.value.cacheEnabledOsmApi
            val cachedUsersMap = if (cacheEnabled) userDao.getUsersByIds(uidsToSync).associateBy { it.id } else emptyMap()

            uidsToSync.forEach { uid ->
                val cached = if (cacheEnabled) cachedUsersMap[uid] else null
                val recent = latestChangesetByUid[uid] ?: return@forEach
                if (cacheEnabled && cached != null && !isUserUpdateRequired(cached, recent)) {
                    val analysis = cached.toAnalysis(recent.createdAt)
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
                } else {
                    staleUids.add(uid)
                }
            }
        }

        staleUids.chunked(BATCH_FETCH_SIZE).forEach { batchIds ->
            val remoteUsers = repository.fetchUsersDetails(batchIds).getOrNull()
            if (remoteUsers != null) {
                remoteUsers.forEach { user ->
                    val recent = latestChangesetByUid[user.id] ?: return@forEach
                    val isWelcomed = welcomedIds.contains(user.id.toString())
                    val analysis = UserAnalyzer.analyze(user, emptyList(), recent, isWelcomed = isWelcomed)
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
                }
            }
        }

        val entities = allProcessedUsers.mapNotNull { it.entityToPersist }
        val activities = allProcessedUsers.mapNotNull { it.areaActivityToPersist }
        if (entities.isNotEmpty()) userDao.insertUsers(entities)
        if (activities.isNotEmpty()) userDao.insertOrUpdateUserAreaActivities(activities)

        return allProcessedUsers.size
    }

    private fun buildTimeRange(cursorEnd: String?): String? {
        return cursorEnd?.let { "2004-01-01T00:00:00Z,$it" }
    }



    private suspend fun loadLocalUsersPage(bbox: String, offset: Int): List<UserUiModel> {
        val state = _uiState.value
        val term = state.searchTerm.trim()
            .replace("\\", "\\\\")
            .replace("_", "\\_")
            .replace("%", "\\%")
        return userDao.getUsersForBBoxFiltered(
            bbox = bbox,
            searchTerm = term,
            minEdits = state.minChanges,
            filterNewcomer = state.filterIsNewcomer,
            filterReturning = state.filterIsReturning,
            filterPowerUser = state.filterIsPowerUser,
            limit = LOCAL_PAGE_SIZE,
            offset = offset
        )
            .map { it.toUiModel() }
            .let { users -> sortUsersByAreaActivity(users) }
    }



    private suspend fun loadCachedUsersOrShowError(bbox: String, fallbackErrorMessage: String) {
        val cachedUsers = loadLocalUsersPage(bbox, currentPageOffset)
        if (cachedUsers.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                users = cachedUsers,
                errorMessage = null,
                isLoading = false,
                hasReachedEnd = cachedUsers.size < LOCAL_PAGE_SIZE
            )
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
        val recentMillis = try {
            java.time.Instant.parse(recent.createdAt).toEpochMilli()
        } catch (e: Exception) {
            Long.MAX_VALUE
        }
        val cachedMillis = cached.lastActiveDate?.let {
            try {
                java.time.Instant.parse(it).toEpochMilli()
            } catch (e: Exception) {
                Long.MIN_VALUE
            }
        } ?: Long.MIN_VALUE
        if (recentMillis > cachedMillis) return true

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


    private suspend fun getWelcomedIds(forceRefresh: Boolean = false): Set<String> {
        return welcomedMutex.withLock {
            if (!forceRefresh) {
                welcomedIdsCache?.let { return@withLock it }
            }
            welcomedUserStorage.getAllWelcomedIds().also { welcomedIdsCache = it }
        }
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
                userDao.updateOsmchaStats(userId, likes, dislikes, now, now)
            }

            is Result.Error -> {
                _uiState.value = _uiState.value.copy(osmchaState = OsmchaState.Error)
            }
        }
    }

    private fun reloadLocalData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                hasReachedEnd = false
                // Keep existing users list to avoid UI flicker
            )
            currentPageOffset = 0
            try {
                val pageUsers = loadLocalUsersPage(_uiState.value.selectedBBox, currentPageOffset)
                _uiState.value = _uiState.value.copy(
                    users = pageUsers,
                    hasReachedEnd = pageUsers.size < LOCAL_PAGE_SIZE
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Error reloading local data")
                _uiState.value = _uiState.value.copy(
                    errorMessage = application.getString(R.string.error_prefix, e.message)
                )
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun updateSearchTerm(term: String) {
        _uiState.value = _uiState.value.copy(searchTerm = term, errorMessage = null)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            reloadLocalData()
        }
    }

    fun searchOsmUserOnline(query: String? = null) {
        val term = (query ?: _uiState.value.searchTerm).trim()
        if (term.isBlank()) return

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null
            )

            try {
                val localUsers = userDao.getUsersForBBoxFiltered(
                    bbox = _uiState.value.selectedBBox,
                    searchTerm = term,
                    minEdits = 0,
                    filterNewcomer = false,
                    filterReturning = false,
                    filterPowerUser = false,
                    limit = 10,
                    offset = 0
                )

                val exactLocalMatch = localUsers.find {
                    it.user.displayName.equals(term, ignoreCase = true)
                }

                if (exactLocalMatch != null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        searchTerm = exactLocalMatch.user.displayName,
                        errorMessage = null
                    )
                    reloadLocalData()
                    return@launch
                }

                if (!_uiState.value.isOnline) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = application.getString(R.string.offline_banner)
                    )
                    return@launch
                }

                val searchResult = repository.searchUserByUsername(term)
                if (searchResult is Result.Success) {
                    val osmUser = searchResult.data
                    val welcomedIds = getWelcomedIds()
                    val isWelcomed = welcomedIds.contains(osmUser.id.toString())

                    val changesetsResult = repository.fetchUserChangesets(userId = osmUser.id, bbox = _uiState.value.selectedBBox, limit = 1)
                    val latestChangeset = changesetsResult.getOrNull()?.firstOrNull()

                    val analysis = UserAnalyzer.analyze(
                        user = osmUser,
                        userChangesets = emptyList(),
                        recentChangeset = latestChangeset,
                        isWelcomed = isWelcomed
                    )

                    val userEntity = osmUser.toEntity(analysis)
                    userDao.insertUser(userEntity)

                    val activityEntity = UserAreaActivityEntity(
                        bbox = _uiState.value.selectedBBox,
                        userId = osmUser.id,
                        lastChangesetDate = analysis.lastActiveDate ?: (latestChangeset?.createdAt ?: ""),
                        lastChangesetId = latestChangeset?.id ?: 0L
                    )
                    userDao.insertOrUpdateUserAreaActivities(listOf(activityEntity))

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        searchTerm = osmUser.displayName,
                        errorMessage = null
                    )
                    reloadLocalData()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = application.getString(R.string.no_results_msg, term)
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error searching OSM user online for term: $term")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = application.getString(R.string.error_search_format, e.message ?: "")
                )
            }
        }
    }

    fun updateMinChanges(min: Int) {
        _uiState.value = _uiState.value.copy(minChanges = min.coerceAtLeast(0))
        reloadLocalData()
    }

    fun toggleNewcomerFilter() {
        _uiState.value = _uiState.value.copy(filterIsNewcomer = !_uiState.value.filterIsNewcomer)
        reloadLocalData()
    }

    fun toggleReturningFilter() {
        _uiState.value = _uiState.value.copy(filterIsReturning = !_uiState.value.filterIsReturning)
        reloadLocalData()
    }

    fun togglePowerUserFilter() {
        _uiState.value = _uiState.value.copy(filterIsPowerUser = !_uiState.value.filterIsPowerUser)
        reloadLocalData()
    }

    fun fetchNextWindowFromNetwork() {
        if (_uiState.value.isPaging || _uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPaging = true, errorMessage = null)
            try {
                fetchNextUsersWindowFromNetwork(_uiState.value.selectedBBox)
                _uiState.value = _uiState.value.copy(lastSyncTimestamp = System.currentTimeMillis())
            } catch (e: Exception) {
                Timber.e(e, "Error fetching next 100 changesets")
            } finally {
                _uiState.value = _uiState.value.copy(isPaging = false)
            }
        }
    }

    fun updateBBox(bbox: String) {
        if (_uiState.value.selectedBBox != bbox) {
            currentPageOffset = 0
            _uiState.value = _uiState.value.copy(selectedBBox = bbox, hasReachedEnd = false)
            viewModelScope.launch {
                areaSyncStateDao.updateIsHistoricalExhausted(bbox, false)
                loadData(forceRefresh = true)
            }
        }
    }

    fun onPullToRefresh() {
        loadData(forceRefresh = true)
    }



    suspend fun getUserById(userId: Long): UserUiModel? {
        val inMemory = _uiState.value.users.find { it.user.id == userId }
        if (inMemory != null) return inMemory
        return userDao.getUserById(userId)?.toUiModel()
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
            }
        }
    }

    fun refreshUser(userId: Long, force: Boolean = false) {
        viewModelScope.launch {
            val cacheEnabled = settings.value.cacheEnabledOsmApi
            val cachedUser = if (cacheEnabled) userDao.getUserById(userId) else null

            // Auto-refresh logic: only if forced OR (not stale according to 24h threshold)
            // Note: USER_CACHE_TTL_MS is 24h
            val isFresh =
                cacheEnabled && cachedUser != null && (System.currentTimeMillis() - cachedUser.lastUpdated) < USER_CACHE_TTL_MS

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

            // Use totalEdits from user detail response instead of fetching changesets
            // firstChangesetDate is already stored in cached entity
            val isWelcomed = welcomedIds.contains(userId.toString())
            val totalEdits = user.changesets?.count ?: 0
            val analysis = UserAnalyzer.analyze(user, emptyList(), null, isWelcomed = isWelcomed).copy(
                totalEdits = totalEdits
            )

            val userEntity = user.toEntity(analysis)

            userDao.insertUser(userEntity)

            val updatedModel = userEntity.toUiModel()

            _uiState.value = _uiState.value.copy(
                users = _uiState.value.users.map { if (it.user.id == userId) updatedModel else it }
            )
        }
    }


}
