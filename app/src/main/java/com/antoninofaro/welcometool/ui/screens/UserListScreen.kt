package com.antoninofaro.welcometool.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.antoninofaro.welcometool.R
import com.antoninofaro.welcometool.data.model.OsmUser
import com.antoninofaro.welcometool.domain.UserAnalysis
import com.antoninofaro.welcometool.ui.components.ProfileAvatar
import com.antoninofaro.welcometool.ui.components.VerticalScrollbar
import com.antoninofaro.welcometool.ui.components.fadingEdge
import com.antoninofaro.welcometool.utils.formatRelativeTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListScreen(
    viewModel: MainViewModel,
    onUserClick: (OsmUser, UserAnalysis) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isSearchVisible by rememberSaveable { mutableStateOf(false) }
    var isAdvancedFiltersVisible by rememberSaveable { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val lazyPagingItems = viewModel.userPagingData.collectAsLazyPagingItems()
    val coroutineScope = rememberCoroutineScope()
    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }

    var shouldScrollToTopOnRefresh by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.searchTerm, uiState.minChanges, uiState.filterIsNewcomer, uiState.filterIsReturning, uiState.filterIsPowerUser, uiState.selectedBBox) {
        shouldScrollToTopOnRefresh = true
        listState.scrollToItem(0)
    }

    LaunchedEffect(lazyPagingItems.loadState.refresh) {
        val refreshState = lazyPagingItems.loadState.refresh
        if (refreshState is LoadState.Loading) {
            shouldScrollToTopOnRefresh = true
            listState.scrollToItem(0)
        } else if (refreshState is LoadState.NotLoading && shouldScrollToTopOnRefresh) {
            if (lazyPagingItems.itemCount > 0) {
                listState.scrollToItem(0)
            }
            shouldScrollToTopOnRefresh = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_title),
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
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
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
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.scroll_to_top_desc)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search & Filters
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
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_mapper)) },
                    leadingIcon = {
                        IconButton(onClick = {
                            if (uiState.searchTerm.isNotBlank()) {
                                viewModel.searchOsmUserOnline()
                            }
                        }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.search_icon_desc)
                            )
                        }
                    },
                    trailingIcon = {
                        if (uiState.searchTerm.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchTerm("") }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.clear_search_desc)
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = {
                            if (uiState.searchTerm.isNotBlank()) {
                                viewModel.searchOsmUserOnline()
                            }
                        }
                    )
                )
            }

            // Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.filterIsNewcomer,
                    onClick = { viewModel.toggleNewcomerFilter() },
                    label = { Text(stringResource(R.string.novices)) },
                    leadingIcon = {
                        if (uiState.filterIsNewcomer) Icon(
                            Icons.Default.Check,
                            contentDescription = stringResource(R.string.selected_desc),
                            modifier = Modifier.size(18.dp)
                        ) else null
                    }
                )
                FilterChip(
                    selected = uiState.filterIsReturning,
                    onClick = { viewModel.toggleReturningFilter() },
                    label = { Text(stringResource(R.string.returning_filter)) },
                    leadingIcon = {
                        if (uiState.filterIsReturning) Icon(
                            Icons.Default.Check,
                            contentDescription = stringResource(R.string.selected_desc),
                            modifier = Modifier.size(18.dp)
                        ) else null
                    }
                )
                FilterChip(
                    selected = uiState.filterIsPowerUser,
                    onClick = { viewModel.togglePowerUserFilter() },
                    label = { Text(stringResource(R.string.power_users)) },
                    leadingIcon = {
                        if (uiState.filterIsPowerUser) Icon(
                            Icons.Default.Check,
                            contentDescription = stringResource(R.string.selected_desc),
                            modifier = Modifier.size(18.dp)
                        ) else null
                    }
                )
                FilterChip(
                    selected = isAdvancedFiltersVisible,
                    onClick = { isAdvancedFiltersVisible = !isAdvancedFiltersVisible },
                    label = { Text(stringResource(R.string.advanced_filters)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.advanced_filters),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }

            // Min edits filter
            AnimatedVisibility(
                visible = isAdvancedFiltersVisible,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.min_edits_short),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = if (uiState.minChanges == 0) "" else uiState.minChanges.toString(),
                        onValueChange = { text ->
                            if (text.isEmpty() || text.all { it.isDigit() }) {
                                viewModel.updateMinChanges(text.toIntOrNull() ?: 0)
                            }
                        },
                        modifier = Modifier.width(80.dp),
                        singleLine = true,
                        placeholder = { Text("0") },
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Last sync timestamp (always visible)
            if (uiState.lastSyncTimestamp > 0L) {
                Text(
                    text = stringResource(
                        R.string.data_freshness_label, formatRelativeTime(
                            uiState.lastSyncTimestamp,
                            stringResource(R.string.time_now),
                            stringResource(R.string.time_minutes_ago),
                            stringResource(R.string.time_hours_ago)
                        )
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }

            // Offline banner
            if (!uiState.isOnline) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.offline_banner),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::onPullToRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .fadingEdge()
            ) {
                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val isSearchingByName = uiState.searchTerm.trim().isNotBlank()
                    val isEmpty = lazyPagingItems.itemCount == 0 && lazyPagingItems.loadState.refresh is LoadState.NotLoading

                    when {
                        uiState.errorMessage != null -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Text(
                                        text = uiState.errorMessage
                                            ?: stringResource(R.string.search_error_fallback),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    if (uiState.searchTerm.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = { viewModel.searchOsmUserOnline() }
                                        ) {
                                            Icon(
                                                Icons.Default.Search,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                stringResource(
                                                    R.string.search_osm_online_btn,
                                                    uiState.searchTerm.trim()
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        isEmpty -> {
                            Box(
                                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Text(
                                        text = if (uiState.searchTerm.isNotBlank() || uiState.minChanges > 0 || uiState.filterIsNewcomer || uiState.filterIsReturning || uiState.filterIsPowerUser) {
                                            stringResource(
                                                R.string.no_results_msg,
                                                uiState.searchTerm.trim()
                                            )
                                        } else {
                                            stringResource(R.string.empty_users_msg)
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (uiState.searchTerm.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = { viewModel.searchOsmUserOnline() }
                                        ) {
                                            Icon(
                                                Icons.Default.Search,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                stringResource(
                                                    R.string.search_osm_online_btn,
                                                    uiState.searchTerm.trim()
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            Box(modifier = Modifier.fillMaxSize()) {
                                LazyColumn(
                                    state = listState,
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(
                                        count = lazyPagingItems.itemCount,
                                        key = lazyPagingItems.itemKey { it.user.id }
                                    ) { index ->
                                        val userModel = lazyPagingItems[index]
                                        if (userModel != null) {
                                            UserListItem(
                                                user = userModel.user,
                                                analysis = userModel.analysis,
                                                osmchaLastChecked = userModel.osmchaLastChecked,
                                                onClick = onUserClick
                                            )
                                        }
                                    }

                                    item(key = "users_footer") {
                                        when (val appendState = lazyPagingItems.loadState.append) {
                                            is LoadState.Loading -> {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 16.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator()
                                                }
                                            }
                                            is LoadState.NotLoading -> {
                                                if (!isSearchingByName) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 12.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Button(
                                                            onClick = { viewModel.fetchNextWindowFromNetwork() }
                                                        ) {
                                                            if (uiState.isPaging) {
                                                                CircularProgressIndicator(
                                                                    modifier = Modifier.size(16.dp),
                                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                                    strokeWidth = 2.dp
                                                                )
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                            }
                                                            Text("Cerca altri utenti")
                                                        }
                                                    }
                                                }
                                            }
                                            is LoadState.Error -> {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 16.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = stringResource(
                                                            R.string.error_loading_format,
                                                            appendState.error.message ?: ""
                                                        ),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.error
                                                    )
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
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserListItem(
    user: OsmUser,
    analysis: UserAnalysis,
    osmchaLastChecked: Long,
    onClick: (OsmUser, UserAnalysis) -> Unit
) {
    val datePattern = stringResource(R.string.date_format_pattern)
    Card(
        onClick = { onClick(user, analysis) },
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with profile image or placeholder
            ProfileAvatar(
                displayName = user.displayName,
                imageUrl = user.img?.href,
                modifier = Modifier.size(56.dp),
                contentDescription = stringResource(R.string.profile_photo_desc, user.displayName),
                size = 56
            )

            Spacer(modifier = Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.edits_label, user.changesets?.count ?: 0),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (osmchaLastChecked > 0L) {
                    Text(
                        text = stringResource(
                            R.string.osmcha_summary,
                            analysis.osmchaLikes,
                            analysis.osmchaDislikes,
                            formatRelativeTime(
                                osmchaLastChecked,
                                stringResource(R.string.time_now),
                                stringResource(R.string.time_minutes_ago),
                                stringResource(R.string.time_hours_ago),
                                datePattern
                            )
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val (statusRes, statusColor, statusOnColor) = when {
                        analysis.isNewcomer -> Triple(R.string.status_newcomer, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                        analysis.isReturning -> Triple(R.string.status_returning, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                        analysis.isPowerUser -> Triple(R.string.status_power_user, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                        else -> Triple(R.string.status_regular, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                stringResource(statusRes),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = statusColor,
                            labelColor = statusOnColor,
                            disabledContainerColor = statusColor,
                            disabledLabelColor = statusOnColor
                        ),
                        border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = androidx.compose.ui.graphics.Color.Transparent)
                    )
                    
                    if (analysis.isWelcomed) {
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    stringResource(R.string.welcomed_chip),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                leadingIconContentColor = MaterialTheme.colorScheme.primary,
                                labelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.view_details_desc),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
