package com.antoninofaro.welcometool.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antoninofaro.welcometool.R
import com.antoninofaro.welcometool.data.model.OsmUser
import com.antoninofaro.welcometool.domain.UserAnalysis
import com.antoninofaro.welcometool.ui.components.ProfileAvatar
import com.antoninofaro.welcometool.ui.components.fadingEdge
import com.antoninofaro.welcometool.utils.formatRelativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListScreen(
    viewModel: MainViewModel,
    onUserClick: (OsmUser, UserAnalysis) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

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
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search & Filters
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                value = uiState.searchTerm,
                onValueChange = { viewModel.updateSearchTerm(it) },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_mapper)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_icon_desc)) },
                shape = RoundedCornerShape(12.dp),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { viewModel.performSearch(uiState.searchTerm) }
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search
                )
            )

            // Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
            }

            // Min edits filter
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
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    textStyle = MaterialTheme.typography.bodySmall
                )
            }

            // Last sync timestamp (always visible)
            if (uiState.lastSyncTimestamp > 0L) {
                Text(
                    text = stringResource(R.string.data_freshness_label, formatRelativeTime(
                        uiState.lastSyncTimestamp,
                        stringResource(R.string.time_now),
                        stringResource(R.string.time_minutes_ago),
                        stringResource(R.string.time_hours_ago)
                    )),
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
                    when {
                        uiState.errorMessage != null -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = uiState.errorMessage ?: stringResource(R.string.search_error_fallback),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(24.dp)
                                )
                            }
                        }

                        uiState.filteredUsers.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (uiState.users.isEmpty()) {
                                        stringResource(R.string.empty_users_msg)
                                    } else {
                                        stringResource(R.string.no_results_msg, uiState.searchTerm.trim())
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(24.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        else -> {
                            val isSearchingByName = uiState.searchTerm.trim().isNotBlank()

                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(
                                    items = uiState.filteredUsers,
                                    key = { it.user.id },
                                    contentType = { "user_item" }
                                ) { userModel ->
                                    UserListItem(
                                        user = userModel.user,
                                        analysis = userModel.analysis,
                                        osmchaLastChecked = userModel.osmchaLastChecked,
                                        onClick = onUserClick
                                    )
                                }

                                item(key = "users_footer") {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        if (!isSearchingByName) {
                                            Button(
                                                onClick = { viewModel.loadMoreUsers() },
                                                enabled = !uiState.isPaging,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.height(48.dp)
                                            ) {
                                                Text(if (uiState.isPaging) stringResource(R.string.loading_label) else stringResource(R.string.load_more_btn))
                                            }
                                        }

                                        if (uiState.hasReachedEnd) {
                                            Text(
                                                text = stringResource(R.string.end_of_list),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
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
                        text = stringResource(R.string.osmcha_summary, analysis.osmchaLikes, analysis.osmchaDislikes, formatRelativeTime(osmchaLastChecked, stringResource(R.string.time_now), stringResource(R.string.time_minutes_ago), stringResource(R.string.time_hours_ago), datePattern)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (analysis.isNewcomer) {
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    stringResource(R.string.status_newcomer),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            enabled = false,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                    if (analysis.isReturning) {
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    stringResource(R.string.status_returning),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            enabled = false,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                    if (analysis.isPowerUser) {
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    stringResource(R.string.status_power_user),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            enabled = false,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
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
