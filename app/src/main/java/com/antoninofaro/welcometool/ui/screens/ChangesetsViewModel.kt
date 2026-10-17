package com.antoninofaro.welcometool.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.antoninofaro.welcometool.data.entity.ChangesetEntity
import com.antoninofaro.welcometool.data.local.dao.ChangesetDao
import com.antoninofaro.welcometool.data.model.ChangedElementGroup
import com.antoninofaro.welcometool.data.model.ChangesetUiModel
import com.antoninofaro.welcometool.data.model.OsmChangeset
import com.antoninofaro.welcometool.data.model.Result
import com.antoninofaro.welcometool.data.repository.OsmChaRepository
import com.antoninofaro.welcometool.data.repository.OsmRepository
import com.antoninofaro.welcometool.data.repository.SettingsRepository
import com.antoninofaro.welcometool.utils.ConnectivityObserver
import com.antoninofaro.welcometool.utils.Constants
import com.antoninofaro.welcometool.utils.intersectsBBox
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class ChangesetsUiState(
    val changesets: List<ChangesetUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isPaging: Boolean = false,
    val hasReachedEnd: Boolean = false,
    val searchTerm: String = "",
    val userFilter: String = "",
    val uidFilter: Long = 0L,
    val includeOutsideBBox: Boolean = false,
    val isSearchingById: Boolean = false,
    val filterReviewRequested: Boolean = false,
    val filterAnomalies: Boolean = false,
    val selectedBBox: String = Constants.WORLD_BBOX,
    val selectedAreaName: String = Constants.DEFAULT_AREA_NAME,
    val isOnline: Boolean = true,
    val selectedChangesetElements: ChangedElementGroup? = null,
    val isElementsLoading: Boolean = false
)

@HiltViewModel
class ChangesetsViewModel @Inject constructor(
    private val changesetDao: ChangesetDao,
    private val osmRepository: OsmRepository,
    private val syncManager: com.antoninofaro.welcometool.domain.ChangesetSyncManager,
    private val osmChaRepository: OsmChaRepository,
    private val settingsRepository: SettingsRepository,
    private val connectivityObserver: ConnectivityObserver
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangesetsUiState())
    val uiState: StateFlow<ChangesetsUiState> = _uiState.asStateFlow()

    private var currentPageOffset = 0
    private val PAGE_SIZE = 100

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                if (settings.isLoaded && (_uiState.value.selectedBBox != settings.defaultBBox || _uiState.value.selectedAreaName != settings.defaultAreaName)) {
                    val newBBox = settings.defaultBBox
                    val newName = settings.defaultAreaName
                    _uiState.value = _uiState.value.copy(
                        selectedBBox = newBBox,
                        selectedAreaName = newName
                    )
                    loadLocalChangesets(reset = true)
                    val existing = changesetDao.getChangesetsForBBoxPage(newBBox, 1, 0)
                    if (existing.isEmpty() && newBBox != Constants.WORLD_BBOX) {
                        refreshChangesets()
                    }
                }
            }
        }
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _uiState.value = _uiState.value.copy(isOnline = online)
            }
        }
    }

    fun loadMoreFromNetwork() {
        if (_uiState.value.isLoading || _uiState.value.isPaging) return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPaging = true)
            
            // Find the oldest changeset date to use as closed_before
            val oldest = _uiState.value.changesets.minByOrNull { it.entity.createdAt }
            val closedBefore = oldest?.entity?.createdAt

            val timeRange = if (closedBefore != null) "2005-01-01T00:00:00Z,$closedBefore" else null

            val isUserFilterActive = _uiState.value.userFilter.isNotEmpty() || _uiState.value.uidFilter > 0L

            if (isUserFilterActive) {
                val bboxParam = if (_uiState.value.includeOutsideBBox) null else _uiState.value.selectedBBox
                val userIdParam = if (_uiState.value.uidFilter > 0L) _uiState.value.uidFilter else null
                val usernameParam = if (_uiState.value.userFilter.isNotEmpty()) _uiState.value.userFilter else null

                val result = osmRepository.fetchUserChangesets(
                    userId = userIdParam,
                    bbox = bboxParam,
                    username = usernameParam,
                    timeRange = timeRange,
                    limit = 100
                )

                if (result is com.antoninofaro.welcometool.data.model.Result.Success) {
                    val list = result.data
                    if (list.isNotEmpty()) {
                        val bbox = _uiState.value.selectedBBox
                        val matching = if (bboxParam == null) list else list.filter { it.intersectsBBox(bbox) }
                        changesetDao.insertOrUpdateChangesets(matching.map { it.toEntity(bbox) })
                        currentPageOffset = 0
                        loadLocalChangesets(reset = true)
                    } else {
                        _uiState.value = _uiState.value.copy(isPaging = false, hasReachedEnd = true)
                    }
                } else {
                    _uiState.value = _uiState.value.copy(isPaging = false)
                }
            } else {
                val result = osmRepository.fetchRecentChangesets(
                    bbox = _uiState.value.selectedBBox,
                    timeRange = timeRange,
                    limit = 100
                )

                if (result is com.antoninofaro.welcometool.data.model.Result.Success) {
                    val list = result.data
                    if (list.isNotEmpty()) {
                        val bbox = _uiState.value.selectedBBox
                        val matching = list.filter { it.intersectsBBox(bbox) }
                        changesetDao.insertOrUpdateChangesets(matching.map { it.toEntity(bbox) })
                        currentPageOffset = 0
                        loadLocalChangesets(reset = true)
                    } else {
                        _uiState.value = _uiState.value.copy(isPaging = false, hasReachedEnd = true)
                    }
                } else {
                    _uiState.value = _uiState.value.copy(isPaging = false)
                }
            }
        }
    }

    fun loadLocalChangesets(reset: Boolean = false) {
        if (reset) {
            currentPageOffset = 0
            _uiState.value = _uiState.value.copy(isLoading = true, hasReachedEnd = false)
        } else {
            if (_uiState.value.hasReachedEnd || _uiState.value.isPaging || _uiState.value.isLoading) return
            _uiState.value = _uiState.value.copy(isPaging = true)
        }

        viewModelScope.launch {
            val bbox = _uiState.value.selectedBBox
            val term = _uiState.value.searchTerm
            val userFilter = _uiState.value.userFilter
            val uidFilter = _uiState.value.uidFilter
            val reviewReq = _uiState.value.filterReviewRequested
            val anomalies = _uiState.value.filterAnomalies
            val isUserFilterActive = userFilter.isNotEmpty() || uidFilter > 0L
            val includeOutside = isUserFilterActive && _uiState.value.includeOutsideBBox

            val entities = changesetDao.getChangesetsForBBoxFiltered(
                bbox = bbox,
                ignoreBBox = includeOutside,
                userFilter = userFilter,
                uidFilter = uidFilter,
                searchTerm = term,
                filterReviewRequested = reviewReq,
                filterAnomalies = anomalies,
                limit = PAGE_SIZE,
                offset = currentPageOffset
            ).let { list ->
                if (includeOutside) list else list.filter { it.intersectsBBox(bbox) }
            }

            val newUiModels = entities.map { it.toUiModel(_uiState.value.isOnline) }
            val currentList = if (reset) emptyList() else _uiState.value.changesets

            val updatedList = currentList + newUiModels
            currentPageOffset += PAGE_SIZE

            _uiState.value = _uiState.value.copy(
                changesets = updatedList,
                isLoading = false,
                isPaging = false,
                hasReachedEnd = entities.size < PAGE_SIZE
            )

            if (reset) {
                syncFirst10ChangesetDiffs(entities)
            }
        }
    }

    private fun syncFirst10ChangesetDiffs(entities: List<ChangesetEntity>) {
        val uncalculated = entities.take(10).filter { 
            it.numChanges > 0 && it.createCount == 0 && it.modifyCount == 0 && it.deleteCount == 0 
        }
        
        if (uncalculated.isEmpty()) return

        viewModelScope.launch {
            for (entity in uncalculated) {
                var updated = false
                // Use OSMCha for preview stats.
                val result = osmChaRepository.getChangesetDetail(entity.id)
                if (result is com.antoninofaro.welcometool.data.model.Result.Success) {
                    val detail = result.data
                    val c = detail.create
                    val m = detail.modify
                    val d = detail.delete
                    if (c > 0 || m > 0 || d > 0) {
                        changesetDao.updateDiffCounts(entity.id, c, m, d)
                        updateChangesetInState(entity.id, c, m, d)
                        updated = true
                    }
                }

                // If OSMCha fails (e.g. 404 or 401), fallback to gentle .osc download with delay
                if (!updated) {
                    kotlinx.coroutines.delay(150)
                    val oscResult = osmRepository.fetchChangesetDownload(entity.id)
                    if (!oscResult.isError) {
                        val group = oscResult.getOrNull()
                        if (group != null) {
                            val c = group.creates.size
                            val m = group.modifies.size
                            val d = group.deletes.size
                            changesetDao.updateDiffCounts(entity.id, c, m, d)
                            updateChangesetInState(entity.id, c, m, d)
                        }
                    }
                }
            }
        }
    }

    private fun updateChangesetInState(changesetId: Long, c: Int, m: Int, d: Int) {
        val updatedList = _uiState.value.changesets.map { uiModel ->
            if (uiModel.entity.id == changesetId) {
                uiModel.copy(
                    entity = uiModel.entity.copy(
                        createCount = c,
                        modifyCount = m,
                        deleteCount = d
                    )
                )
            } else uiModel
        }
        _uiState.value = _uiState.value.copy(changesets = updatedList)
    }

    fun refreshChangesets() {
        viewModelScope.launch {
            val showIndicator = _uiState.value.changesets.isNotEmpty()
            _uiState.value = _uiState.value.copy(
                isLoading = !showIndicator,
                isRefreshing = showIndicator
            )
            val bbox = _uiState.value.selectedBBox
            syncManager.executePhaseA(bbox)
            syncManager.executePhaseB(bbox = bbox)
            loadLocalChangesets(true)
            _uiState.value = _uiState.value.copy(isLoading = false, isRefreshing = false)
        }
    }

    fun updateSearchTerm(term: String) {
        val currentUidFilter = if (term.startsWith("uid:", ignoreCase = true) || term.startsWith("idu:", ignoreCase = true)) {
            _uiState.value.uidFilter
        } else {
            0L
        }
        val isUserFilterActive = _uiState.value.userFilter.isNotEmpty() || currentUidFilter > 0L
        _uiState.value = _uiState.value.copy(
            searchTerm = term,
            uidFilter = currentUidFilter,
            includeOutsideBBox = if (isUserFilterActive) _uiState.value.includeOutsideBBox else false
        )
        loadLocalChangesets(true)
    }

    fun fetchUserChangesetsByUid(uid: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearchingById = true)
            val bbox = _uiState.value.selectedBBox
            val isUserFilterActive = _uiState.value.userFilter.isNotEmpty() || uid > 0L
            val bboxParam = if (_uiState.value.includeOutsideBBox && isUserFilterActive) null else bbox
            val result = osmRepository.fetchUserChangesets(userId = uid, bbox = bboxParam, limit = 100)
            if (result is Result.Success) {
                val matching = if (bboxParam == null) result.data else result.data.filter { it.intersectsBBox(bbox) }
                val entities = matching.map { it.toEntity(bbox) }
                changesetDao.insertOrUpdateChangesets(entities)
                _uiState.value = _uiState.value.copy(
                    uidFilter = uid,
                    isSearchingById = false,
                    includeOutsideBBox = if (isUserFilterActive) _uiState.value.includeOutsideBBox else false
                )
                loadLocalChangesets(reset = true)
            } else {
                _uiState.value = _uiState.value.copy(isSearchingById = false)
            }
        }
    }

    fun updateUserFilter(username: String?) {
        val newUsername = username ?: ""
        val isUserFilterActive = newUsername.isNotEmpty() || _uiState.value.uidFilter > 0L
        _uiState.value = _uiState.value.copy(
            userFilter = newUsername,
            includeOutsideBBox = if (isUserFilterActive) _uiState.value.includeOutsideBBox else false
        )
        loadLocalChangesets(true)
    }

    fun clearUidFilter() {
        val isUserFilterActive = _uiState.value.userFilter.isNotEmpty()
        _uiState.value = _uiState.value.copy(
            uidFilter = 0L,
            includeOutsideBBox = if (isUserFilterActive) _uiState.value.includeOutsideBBox else false
        )
        loadLocalChangesets(true)
    }

    fun toggleIncludeOutsideBBox() {
        val isUserFilterActive = _uiState.value.userFilter.isNotEmpty() || _uiState.value.uidFilter > 0L
        if (!isUserFilterActive) return
        _uiState.value = _uiState.value.copy(includeOutsideBBox = !_uiState.value.includeOutsideBBox)
        loadLocalChangesets(true)
    }

    fun searchChangesetById(id: Long, onResult: (Long?) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearchingById = true)
            
            // 1. Check local DB first
            val local = changesetDao.getChangesetById(id)
            if (local != null) {
                _uiState.value = _uiState.value.copy(isSearchingById = false)
                onResult(id)
                return@launch
            }
            
            // 2. If not in DB, fetch from API
            val result = osmRepository.fetchChangesetById(id)
            if (result is Result.Success) {
                val bbox = _uiState.value.selectedBBox
                val entity = result.data.toEntity(bbox)
                changesetDao.insertOrUpdateChangesets(listOf(entity))
                loadLocalChangesets(reset = true)
                _uiState.value = _uiState.value.copy(isSearchingById = false)
                onResult(id)
            } else {
                _uiState.value = _uiState.value.copy(isSearchingById = false)
                onResult(null)
            }
        }
    }

    fun toggleFilterReviewRequested() {
        _uiState.value = _uiState.value.copy(filterReviewRequested = !_uiState.value.filterReviewRequested)
        loadLocalChangesets(true)
    }

    fun loadChangesetElements(changesetId: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isElementsLoading = true, selectedChangesetElements = null)
            val result = osmRepository.fetchChangesetDownload(changesetId)
            if (!result.isError) {
                val group = result.getOrNull()
                _uiState.value = _uiState.value.copy(selectedChangesetElements = group)
                if (group != null) {
                    val c = group.creates.size
                    val m = group.modifies.size
                    val d = group.deletes.size
                    changesetDao.updateDiffCounts(changesetId, c, m, d)
                    updateChangesetInState(changesetId, c, m, d)
                }
            }
            _uiState.value = _uiState.value.copy(isElementsLoading = false)
        }
    }
    
    fun refreshOsmchaDetails(changesetId: Long) {
        viewModelScope.launch {
            val result = osmChaRepository.getChangesetDetail(changesetId)
            val detail = result.getOrNull()
            if (detail != null) {
                val reasonsJson = if (detail.reasons.isNotEmpty()) {
                    Json.encodeToString(detail.reasons.map { it.name })
                } else null
                
                changesetDao.updateOsmchaDetails(
                    changesetId = changesetId,
                    score = if (detail.is_suspect || detail.harmful == true) -1 else 0,
                    isSuspect = detail.is_suspect,
                    harmful = detail.harmful,
                    reasonsJson = reasonsJson,
                    createCount = detail.create,
                    modifyCount = detail.modify,
                    deleteCount = detail.delete,
                )
                
                val updatedEntity = changesetDao.getChangesetById(changesetId)
                if (updatedEntity != null) {
                    val updatedList = _uiState.value.changesets.map { 
                        if (it.entity.id == changesetId) updatedEntity.toUiModel(_uiState.value.isOnline) else it 
                    }
                    _uiState.value = _uiState.value.copy(changesets = updatedList)
                }
            }
        }
    }

    private fun OsmChangeset.toEntity(bbox: String): ChangesetEntity {
        val tagsJson = if (!tags.isNullOrEmpty()) {
            Json.encodeToString(tags)
        } else null
        return ChangesetEntity(
            id = id,
            bbox = bbox,
            createdAt = createdAt,
            closedAt = closedAt,
            open = open,
            user = user,
            uid = uid,
            minLat = minLat,
            minLon = minLon,
            maxLat = maxLat,
            maxLon = maxLon,
            numChanges = totalChanges,
            commentsCount = commentsCount,
            tagsJson = tagsJson,
            etag = null,
            lastModified = null
        )
    }

    private fun ChangesetEntity.toUiModel(isOnline: Boolean): ChangesetUiModel {
        val reasonsList = try {
            if (!osmchaReasonsJson.isNullOrBlank()) {
                Json.decodeFromString<List<String>>(osmchaReasonsJson)
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        return ChangesetUiModel(
            entity = this,
            reasons = reasonsList,
            isOnline = isOnline
        )
    }
}
