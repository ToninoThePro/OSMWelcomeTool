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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    val isSearchingById: Boolean = false,
    val filterReviewRequested: Boolean = false,
    val filterAnomalies: Boolean = false,
    val selectedBBox: String = Constants.ITALY_BBOX,
    val isOnline: Boolean = true,
    val selectedChangesetElements: ChangedElementGroup? = null,
    val isElementsLoading: Boolean = false
)

@HiltViewModel
class ChangesetsViewModel @Inject constructor(
    private val changesetDao: ChangesetDao,
    private val osmRepository: OsmRepository,
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
            val savedBBox = settingsRepository.settingsFlow.first().defaultBBox
            _uiState.value = _uiState.value.copy(selectedBBox = savedBBox)
            loadLocalChangesets(true)
            val existing = changesetDao.getChangesetsForBBoxPage(savedBBox, 1, 0)
            if (existing.isEmpty()) {
                refreshChangesets()
            }
        }
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                _uiState.value = _uiState.value.copy(isOnline = online)
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

            val entities = changesetDao.getChangesetsForBBoxFiltered(
                bbox = bbox,
                userFilter = userFilter,
                uidFilter = uidFilter,
                searchTerm = term,
                filterReviewRequested = reviewReq,
                filterAnomalies = anomalies,
                limit = PAGE_SIZE,
                offset = currentPageOffset
            )

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
            uncalculated.forEach { entity ->
                val result = osmRepository.fetchChangesetDownload(entity.id)
                if (!result.isError) {
                    val group = result.getOrNull()
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
            val result = osmRepository.fetchRecentChangesets(bbox = bbox, limit = 100)
            if (result is Result.Success) {
                val list = result.data
                if (list.isNotEmpty()) {
                    changesetDao.insertOrUpdateChangesets(list.map { it.toEntity(bbox) })
                }
            }
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
        _uiState.value = _uiState.value.copy(searchTerm = term, uidFilter = currentUidFilter)
        loadLocalChangesets(true)
    }

    fun fetchUserChangesetsByUid(uid: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearchingById = true)
            val result = osmRepository.fetchUserChangesets(uid, 100)
            if (result is Result.Success) {
                val bbox = _uiState.value.selectedBBox
                val entities = result.data.map { it.toEntity(bbox) }
                changesetDao.insertOrUpdateChangesets(entities)
                _uiState.value = _uiState.value.copy(uidFilter = uid, isSearchingById = false)
                loadLocalChangesets(reset = true)
            } else {
                _uiState.value = _uiState.value.copy(isSearchingById = false)
            }
        }
    }

    fun updateUserFilter(username: String?) {
        _uiState.value = _uiState.value.copy(userFilter = username ?: "")
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




    fun fetchMoreUserChangesets(uid: Long) {
        viewModelScope.launch {
            val result = osmRepository.fetchUserChangesets(uid, 10)
            if (result is Result.Success) {
                val bbox = _uiState.value.selectedBBox
                changesetDao.insertOrUpdateChangesets(result.data.map { it.toEntity(bbox) })
                loadLocalChangesets(reset = true)
            }
        }
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
