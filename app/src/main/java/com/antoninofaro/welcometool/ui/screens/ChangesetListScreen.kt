package com.antoninofaro.welcometool.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.InputChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antoninofaro.welcometool.R
import com.antoninofaro.welcometool.data.model.ChangesetUiModel
import com.antoninofaro.welcometool.ui.components.ChangesetCard
import com.antoninofaro.welcometool.ui.components.VerticalScrollbar
import com.antoninofaro.welcometool.ui.components.fadingEdge
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangesetListScreen(
    viewModel: ChangesetsViewModel,
    initialUserFilter: String? = null,
    onChangesetClick: (ChangesetUiModel) -> Unit,
    onClearUserFilter: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isSearchVisible by rememberSaveable { mutableStateOf(false) }
    
    LaunchedEffect(initialUserFilter) {
        if (initialUserFilter != null) {
            viewModel.updateUserFilter(initialUserFilter)
        }
    }
    
    LaunchedEffect(uiState.searchTerm) {
        val rawTerm = uiState.searchTerm.trim()
        if (rawTerm.isEmpty()) return@LaunchedEffect
        
        delay(500)
        if (uiState.isSearchingById) return@LaunchedEffect

        when {
            // Changeset ID search by prefix #123 or id:123
            rawTerm.startsWith("#") && rawTerm.drop(1).all { it.isDigit() } && rawTerm.length > 1 -> {
                val id = rawTerm.drop(1).toLongOrNull()
                if (id != null) viewModel.searchChangesetById(id) {}
            }
            rawTerm.startsWith("id:", ignoreCase = true) && rawTerm.substring(3).all { it.isDigit() } && rawTerm.length > 3 -> {
                val id = rawTerm.substring(3).toLongOrNull()
                if (id != null) viewModel.searchChangesetById(id) {}
            }
            // User ID search by prefix uid:123 or idu:123
            rawTerm.startsWith("uid:", ignoreCase = true) || rawTerm.startsWith("idu:", ignoreCase = true) -> {
                val digits = rawTerm.substring(4)
                if (digits.all { it.isDigit() } && digits.isNotEmpty()) {
                    val uid = digits.toLongOrNull()
                    if (uid != null) viewModel.fetchUserChangesetsByUid(uid)
                }
            }
            // Default pure digits -> Changeset ID
            rawTerm.all { it.isDigit() } -> {
                val id = rawTerm.toLongOrNull()
                if (id != null) viewModel.searchChangesetById(id) {}
            }
        }
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }

    LaunchedEffect(uiState.selectedBBox, uiState.userFilter, uiState.searchTerm, uiState.filterReviewRequested, uiState.filterAnomalies) {
        listState.scrollToItem(0)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Changesets",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = {
                        isSearchVisible = !isSearchVisible
                        if (!isSearchVisible && uiState.searchTerm.isNotEmpty()) {
                            viewModel.updateSearchTerm("")
                        }
                    }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.search_icon_desc)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = showScrollToTop,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Scroll to top")
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(modifier = Modifier.fillMaxSize()) {
                
                AnimatedVisibility(
                    visible = !uiState.isOnline,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Offline. Showing cached changesets.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
                
                AnimatedVisibility(
                    visible = isSearchVisible,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        value = uiState.searchTerm,
                        onValueChange = { viewModel.updateSearchTerm(it) },
                        placeholder = { Text("Search by user, comment or id...") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search"
                            )
                        },
                        shape = RoundedCornerShape(24.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Search
                        ),
                        trailingIcon = {
                            if (uiState.searchTerm.all { it.isDigit() } && uiState.searchTerm.isNotEmpty()) {
                                if (uiState.isSearchingById) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    IconButton(onClick = { viewModel.updateSearchTerm("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                    }
                                }
                            } else if (uiState.searchTerm.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchTerm("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        }
                    )
                }

                // Filter Chips Row
                val isUserFilterActive = uiState.userFilter.isNotEmpty() || uiState.uidFilter > 0L

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.userFilter.isNotEmpty()) {
                        InputChip(
                            selected = true,
                            onClick = {
                                viewModel.updateUserFilter(null)
                                onClearUserFilter()
                            },
                            label = { Text(stringResource(R.string.filter_by_user, uiState.userFilter)) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Remove filter",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                    if (uiState.uidFilter > 0L) {
                        InputChip(
                            selected = true,
                            onClick = {
                                viewModel.clearUidFilter()
                            },
                            label = { Text("User ID: ${uiState.uidFilter}") },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Remove filter",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                    if (isUserFilterActive) {
                        val areaLabel = if (uiState.selectedAreaName.isNotBlank()) {
                            uiState.selectedAreaName
                        } else {
                            stringResource(R.string.default_area)
                        }
                        FilterChip(
                            selected = uiState.includeOutsideBBox,
                            onClick = { viewModel.toggleIncludeOutsideBBox() },
                            label = { Text(stringResource(R.string.include_outside_bbox, areaLabel)) },
                            leadingIcon = if (uiState.includeOutsideBBox) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else null
                        )
                    }
                    /* TODO: Re-enable when anomalies filter is ready
                    FilterChip(
                        selected = uiState.filterAnomalies,
                        onClick = { viewModel.toggleFilterAnomalies() },
                        label = { Text("Anomalies") },
                        leadingIcon = if (uiState.filterAnomalies) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else null
                    )
                    */
                    FilterChip(
                        selected = uiState.filterReviewRequested,
                        onClick = { viewModel.toggleFilterReviewRequested() },
                        label = { Text("Review Requested") },
                        leadingIcon = if (uiState.filterReviewRequested) {
                            {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else null
                    )
                }

                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = viewModel::refreshChangesets,
                    modifier = Modifier
                        .fillMaxSize()
                        .fadingEdge()
                ) {
                    if (uiState.isLoading && uiState.changesets.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (uiState.changesets.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No changesets found. Pull down to refresh.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(uiState.changesets) { changeset ->
                                    ChangesetCard(
                                        uiModel = changeset,
                                        onClick = { onChangesetClick(changeset) }
                                    )
                                }

                                if (uiState.isPaging) {
                                    item {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                        }
                                    }
                                } else if (uiState.hasReachedEnd && uiState.changesets.isNotEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            androidx.compose.material3.Button(
                                                onClick = { viewModel.loadMoreFromNetwork() }
                                            ) {
                                                androidx.compose.material3.Text("Carica altri 100 changeset")
                                            }
                                        }
                                    }
                                }
                            }

                            VerticalScrollbar(
                                listState = listState,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .padding(end = 4.dp)
                            )
                        }
                        
                        LaunchedEffect(listState) {
                            snapshotFlow { 
                                val layoutInfo = listState.layoutInfo
                                val totalItems = layoutInfo.totalItemsCount
                                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                lastVisible >= totalItems - 5
                            }.collect { shouldLoadMore ->
                                if (shouldLoadMore && !uiState.hasReachedEnd && !uiState.isPaging && !uiState.isLoading) {
                                    viewModel.loadLocalChangesets()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
