package com.antoninofaro.welcometool.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antoninofaro.welcometool.data.entity.UserEntity
import com.antoninofaro.welcometool.data.entity.UserAreaActivityEntity
import com.antoninofaro.welcometool.data.local.UserLocalRepository
import com.antoninofaro.welcometool.data.local.model.UserAreaActivityWithUser
import com.antoninofaro.welcometool.data.model.CountWrapper
import com.antoninofaro.welcometool.data.model.OsmChangeset
import com.antoninofaro.welcometool.data.model.OsmUser
import com.antoninofaro.welcometool.data.model.Result
import com.antoninofaro.welcometool.data.model.UserImage
import com.antoninofaro.welcometool.data.repository.OsmRepository
import com.antoninofaro.welcometool.data.repository.WelcomedUserStorage
import com.antoninofaro.welcometool.domain.UserAnalysis
import com.antoninofaro.welcometool.domain.UserAnalyzer
import com.antoninofaro.welcometool.utils.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import javax.inject.Inject

/**
 * Presenter-layer model containing user data combined with community analysis metrics.
 */
data class UserUiModel(
    val user: OsmUser,
    val analysis: UserAnalysis,
    val lastUpdated: Long = 0L
)

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
    val filterIsPowerUser: Boolean = false
)

/**
 * Coordinator ViewModel for the main user-listing UI.
 * Manages fetching of OpenStreetMap changesets and orchestrates parallel user profile analysis
 * using the [OsmRepository], [UserAnalyzer], and various local storage handlers.
 * It provides pagination, filtering, and a synchronized state flow holding [MainUiState].
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: OsmRepository,
    private val welcomedUserStorage: WelcomedUserStorage,
    private val osmChaRepository: com.antoninofaro.welcometool.data.repository.OsmChaRepository,
    private val localRepository: UserLocalRepository,
) : ViewModel() {

    companion object {
        private const val USER_CACHE_TTL_MS = 24 * 60 * 60 * 1000L
        private const val LOCAL_PAGE_SIZE = 100
        private const val MAX_CONCURRENT_USER_FETCH = 6
        private const val MAX_SCAN_WINDOWS = 6
        private const val MAX_RECENT_CHANGESETS = 100
        private const val OSM_EPOCH_START = "2005-01-01T00:00:00Z"
    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val osmchaLoadedUserIds = mutableSetOf<Long>()
    private var welcomedIdsCache: Set<String>? = null
    private var oldestChangesetCursor: String? = null
    private var currentPageOffset: Int = 0

    private data class ProcessedUser(
        val uiModel: UserUiModel,
        val entityToPersist: UserEntity?,
        val areaActivityToPersist: UserAreaActivityEntity?
    )

    /**
     * Initializes or refreshes the list of users based on the currently selected bounding box.
     * When [forceRefresh] is requested, the application bypasses short-circuit cache checks
     * and forces a network sync using [syncRecentUsersForCurrentBBox] if needed.
     *
     * @param forceRefresh Whether to force a fresh data sync instead of serving cached contents right away.
     */
    fun loadData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
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

                if (pageUsers.isNotEmpty() && !forceRefresh) {
                    val totalCount = localRepository.getUserCountForBBox(selectedBBox)
                    _uiState.value = _uiState.value.copy(
                        users = pageUsers,
                        filteredUsers = pageUsers,
                        hasReachedEnd = pageUsers.size < LOCAL_PAGE_SIZE ||
                            currentPageOffset + pageUsers.size >= totalCount
                    )
                    applyFilters()
                    return@launch
                }

                syncRecentUsersForCurrentBBox(selectedBBox)

                val refreshedUsers = loadLocalUsersPage(selectedBBox, currentPageOffset)
                val totalCount = localRepository.getUserCountForBBox(selectedBBox)

                if (refreshedUsers.isEmpty()) {
                    loadCachedUsersOrShowError(selectedBBox, "Nessun utente recuperato")
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    users = refreshedUsers,
                    filteredUsers = refreshedUsers,
                    hasReachedEnd = refreshedUsers.size < LOCAL_PAGE_SIZE ||
                        currentPageOffset + refreshedUsers.size >= totalCount
                )
                applyFilters()

                Timber.d(
                    "Loaded ${refreshedUsers.size} utenti locali per bbox $selectedBBox, offset=$currentPageOffset, next cursor=$oldestChangesetCursor"
                )

            } catch (e: Exception) {
                Timber.e(e, "Error loading data")
                loadCachedUsersOrShowError(selectedBBox, "Errore: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false, isRefreshing = false)
            }
        }
    }

    /**
     * Coordinates the process of fetching the most recent changesets for a selected bounding box,
     * extracting unique user IDs, checking local cache staleness, and processing any missing users in parallel.
     *
     * @param bbox Bounding box string used for limiting the geographic area.
     */
    private suspend fun syncRecentUsersForCurrentBBox(bbox: String) {
        val welcomedIds = getWelcomedIds()
        val semaphore = Semaphore(MAX_CONCURRENT_USER_FETCH)
        val collectedUsers = mutableListOf<ProcessedUser>()
        var scanCursor = oldestChangesetCursor
        val seenUserIds = mutableSetOf<Long>()

        for (window in 0 until MAX_SCAN_WINDOWS) {
            val changesetsResult = repository.fetchRecentChangesets(
                bbox,
                buildTimeRange(scanCursor),
                MAX_RECENT_CHANGESETS
            )

            if (changesetsResult !is Result.Success) {
                if (collectedUsers.isEmpty()) {
                    throw IllegalStateException(
                        "Errore nel caricamento dei changeset: ${changesetsResult.exceptionOrNull()?.message}"
                    )
                }
                Timber.w(changesetsResult.exceptionOrNull(), "Stopping scan on remote error")
                break
            }

            val changesets = changesetsResult.data
            if (changesets.isEmpty()) {
                Timber.d("No more changesets available for scan")
                break
            }

            val newUids = changesets
                .map { it.uid }
                .distinct()
                .filterNot { seenUserIds.contains(it) }

            if (newUids.isNotEmpty()) {
                val changesetsByUid = changesets.reversed().associateBy { it.uid }
                val processedBatch = kotlinx.coroutines.coroutineScope {
                    val deferredResults = newUids.map { uid ->
                        async {
                            semaphore.withPermit {
                                processUser(uid, bbox, changesetsByUid, welcomedIds)
                            }
                        }
                    }
                    deferredResults.awaitAll().filterNotNull()
                }
                if (processedBatch.isNotEmpty()) {
                    collectedUsers.addAll(processedBatch)
                    seenUserIds.addAll(processedBatch.map { it.uiModel.user.id })

                    val usersToPersist = processedBatch.mapNotNull { it.entityToPersist }
                    val areaActivitiesToPersist = processedBatch.mapNotNull { it.areaActivityToPersist }

                    if (usersToPersist.isNotEmpty()) {
                        localRepository.saveUsers(usersToPersist)
                    }
                    if (areaActivitiesToPersist.isNotEmpty()) {
                        localRepository.saveUserAreaActivities(areaActivitiesToPersist)
                    }
                }
            }

            val nextCursor = computeOlderCursor(changesets)
            if (nextCursor == null || nextCursor == scanCursor) {
                break
            }

            oldestChangesetCursor = nextCursor
            scanCursor = nextCursor

            if (collectedUsers.size >= LOCAL_PAGE_SIZE) {
                Timber.d("Collected enough users for local cache refresh in window #$window")
                break
            }
        }
    }

    private suspend fun loadLocalUsersPage(bbox: String, offset: Int): List<UserUiModel> {
        return localRepository.getUsersForBBoxPage(bbox, LOCAL_PAGE_SIZE, offset)
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

    private suspend fun processUser(
        uid: Long,
        bbox: String,
        changesetsByUid: Map<Long, OsmChangeset>,
        welcomedIds: Set<String>
    ): ProcessedUser? = kotlinx.coroutines.coroutineScope {
        val recentChangeset = changesetsByUid[uid] ?: return@coroutineScope null

        // TODO: applicare la preferenza settings.cacheEnabled per bypassare la cache quando disattivata.
        // Check local cache first to avoid redundant network requests
        // But refresh if older than 24 hours
        val cachedUser = localRepository.getUserById(uid)
        val isStale = isUserCacheStale(cachedUser)

        if (cachedUser != null && !isStale) {
            val analysis = cachedUser.toAnalysis(recentChangeset.createdAt)
                .copy(isWelcomed = welcomedIds.contains(uid.toString()))

            return@coroutineScope ProcessedUser(
                uiModel = UserUiModel(cachedUser.toOsmUser(), analysis, cachedUser.lastUpdated),
                entityToPersist = null,
                areaActivityToPersist = UserAreaActivityEntity(
                    bbox = bbox,
                    userId = uid,
                    lastChangesetDate = recentChangeset.createdAt,
                    lastChangesetId = recentChangeset.id
                )
            )
        }

        // Fetch user detail and history in parallel
        val userDeferred = async { repository.fetchUserDetail(uid) }
        val historyDeferred = async { repository.fetchUserChangesets(uid) }

        val userResult = userDeferred.await()
        val historyResult = historyDeferred.await()

        if (userResult !is Result.Success) {
            Timber.w("Failed to fetch user $uid: ${userResult.exceptionOrNull()?.message}")
            return@coroutineScope null
        }

        val user = userResult.data
        val userHistory = historyResult.getOrDefault(emptyList())

        // Analyze user with welcomed status (OSMcha on-demand)
        val analysis = UserAnalyzer.analyze(user, userHistory, recentChangeset)
            .copy(isWelcomed = welcomedIds.contains(uid.toString()))

        val userEntity = UserEntity(
            id = user.id,
            displayName = user.displayName,
            accountCreated = user.accountCreated,
            description = user.description,
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
            imgUrl = user.img?.href
        )

        val areaActivityEntity = UserAreaActivityEntity(
            bbox = bbox,
            userId = user.id,
            lastChangesetDate = recentChangeset.createdAt,
            lastChangesetId = recentChangeset.id
        )

        return@coroutineScope ProcessedUser(
            uiModel = UserUiModel(user, analysis, userEntity.lastUpdated),
            entityToPersist = userEntity,
            areaActivityToPersist = areaActivityEntity
        )
    }

    private fun sortUsersByAreaActivity(users: List<UserUiModel>): List<UserUiModel> {
        return users.sortedWith(
            compareByDescending<UserUiModel> { it.analysis.lastActiveDate ?: "" }
                .thenByDescending { it.lastUpdated }
        )
    }

    private fun isUserCacheStale(cachedUser: UserEntity?): Boolean {
        return cachedUser == null || (System.currentTimeMillis() - cachedUser.lastUpdated > USER_CACHE_TTL_MS)
    }

    private suspend fun getWelcomedIds(forceRefresh: Boolean = false): Set<String> {
        if (!forceRefresh) {
            welcomedIdsCache?.let { return it }
        }
        return welcomedUserStorage.getAllWelcomedIds().also { welcomedIdsCache = it }
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
        return UserUiModel(osmUser, analysis, this.lastUpdated)
    }

    /**
     * Retrieves OSMCha metrics iteratively for a single user given their [userId].
     * Once loaded, this state replaces existing matching UI models inside the internal state pipeline.
     *
     * @param userId The specific identifier matching [OsmUser.id] whose reputation needs checking.
     */
    fun loadOsmchaForUser(userId: Long) {
        if (osmchaLoadedUserIds.contains(userId)) return
        osmchaLoadedUserIds.add(userId)

        viewModelScope.launch {
            val currentUser = _uiState.value.users.find { it.user.id == userId }
                ?: _uiState.value.filteredUsers.find { it.user.id == userId }

            if (currentUser == null) return@launch

            val (likes, dislikes) = osmChaRepository.getUserOsmChaStats(currentUser.user.displayName)

            val updatedUsers = _uiState.value.users.map { userModel ->
                if (userModel.user.id == userId) {
                    userModel.copy(
                        analysis = userModel.analysis.copy(
                            osmchaLikes = likes,
                            osmchaDislikes = dislikes
                        )
                    )
                } else {
                    userModel
                }
            }

            _uiState.value = _uiState.value.copy(users = updatedUsers)
            applyFilters()
        }
    }

    /**
     * Updates the underlying search string applied to in-memory user lists.
     *
     * @param term The phrase or characters to search against the names of currently loaded users.
     */
    fun updateSearchTerm(term: String) {
        _uiState.value = _uiState.value.copy(searchTerm = term)
        applyFilters()
    }

    /**
     * Executes a specialized local database lookup to discover a user bypassing the main sync timeline stream.
     * Overrides current UI content directly with search matching results if successful.
     *
     * @param query The target username segment to search against the system cache via SQL LIKE queries.
     */
    fun performSearch(query: String) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Inserisci un nome utente valido")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                val searchResults = localRepository.searchUsersForBBox(
                    _uiState.value.selectedBBox,
                    "%$normalizedQuery%"
                ).map { it.toUiModel() }

                if (searchResults.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Utente non presente nella cache locale per la zona selezionata"
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    users = sortUsersByAreaActivity(
                        (_uiState.value.users + searchResults).distinctBy { it.user.id }
                    ),
                    filteredUsers = searchResults,
                    isLoading = false,
                    searchTerm = normalizedQuery,
                    filterIsNewcomer = false,
                    filterIsPowerUser = false
                )

                applyFilters()


            } catch (e: Exception) {
                Timber.e(e, "Error searching user manually: $normalizedQuery")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Errore durante la ricerca: ${e.message}"
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
                val totalCount = localRepository.getUserCountForBBox(_uiState.value.selectedBBox)

                currentPageOffset = nextOffset

                _uiState.value = _uiState.value.copy(
                    users = mergedUsers,
                    hasReachedEnd = nextOffset + nextPage.size >= totalCount ||
                        nextPage.size < LOCAL_PAGE_SIZE
                )
                applyFilters()
            } catch (e: Exception) {
                Timber.e(e, "Error loading more users")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Errore durante il caricamento: ${e.message}"
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

    fun toggleNewcomerFilter() {
        _uiState.value = _uiState.value.copy(filterIsNewcomer = !_uiState.value.filterIsNewcomer)
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
            lastUpdated = areaLastUpdated
        )
    }

    private fun applyFilters() {
        val current = _uiState.value
        val normalizedSearchTerm = current.searchTerm.trim()
        val filtered = current.users.filter { uiModel ->
            val matchesSearch = if (normalizedSearchTerm.isBlank()) {
                true // Mostra tutti gli utenti quando searchTerm è vuoto
            } else {
                uiModel.user.displayName.contains(normalizedSearchTerm, ignoreCase = true)
            }
            val matchesNewcomer =
                if (current.filterIsNewcomer) uiModel.analysis.isNewcomer else true
            val matchesPowerUser =
                if (current.filterIsPowerUser) uiModel.analysis.isPowerUser else true

            matchesSearch && matchesNewcomer && matchesPowerUser
        }
        _uiState.value = current.copy(filteredUsers = filtered)
    }

    fun toggleWelcomed(userId: Long, currentStatus: Boolean) {
        viewModelScope.launch {
            welcomedUserStorage.setWelcomed(userId, !currentStatus)
            localRepository.updateWelcomedStatus(userId, !currentStatus)
            welcomedIdsCache = null

            // Update local state immediately
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

    fun refreshUser(userId: Long) {
        viewModelScope.launch {
            // TODO: applicare la preferenza settings.cacheEnabled anche nel refresh puntuale del dettaglio.
            val cachedUser = localRepository.getUserById(userId)
            if (cachedUser != null && !isUserCacheStale(cachedUser)) {
                // Cache fresca: evitiamo roundtrip di rete non necessari quando si apre il dettaglio.
                return@launch
            }

            val userResult = repository.fetchUserDetail(userId)
            if (userResult !is Result.Success) {
                Timber.w("Failed to refresh user $userId")
                return@launch
            }

            val user = userResult.data
            // Fetch history for analysis (optional but good for completeness)
            val historyResult = repository.fetchUserChangesets(userId)
            val userHistory = historyResult.getOrDefault(emptyList())

            val welcomedIds = getWelcomedIds()

            // We don't have the recent changeset here easily, passing null.
            // This might affect analysis slightly if it relies on specific changeset details.
            val analysis = UserAnalyzer.analyze(user, userHistory, null)
                .copy(isWelcomed = welcomedIds.contains(userId.toString()))

            val userEntity = user.toEntity(analysis)

            localRepository.saveUser(userEntity)

            val updatedModel = userEntity.toUiModel()

            _uiState.value = _uiState.value.copy(
                users = sortUsersByAreaActivity(
                    _uiState.value.users.map { if (it.user.id == userId) updatedModel else it }
                )
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
