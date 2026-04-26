package com.antoninofaro.welcometool.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antoninofaro.welcometool.R
import com.antoninofaro.welcometool.data.model.OsmUser
import com.antoninofaro.welcometool.domain.UserAnalysis
import com.antoninofaro.welcometool.ui.components.ProfileAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListScreen(
    viewModel: MainViewModel,
    onUserClick: (OsmUser, UserAnalysis) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OSM Welcome Tool") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search & Filters
            @Suppress("DEPRECATION")
            SearchBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                query = uiState.searchTerm,
                onQueryChange = { viewModel.updateSearchTerm(it) },
                onSearch = { viewModel.performSearch(it) },
                active = false,
                onActiveChange = { },
                placeholder = { Text(stringResource(R.string.search_mapper)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Cerca") }
            ) {
            }

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
                            contentDescription = "Selezionato"
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
                            contentDescription = "Selezionato"
                        ) else null
                    }
                )
            }

            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = viewModel::onPullToRefresh,
                modifier = Modifier.fillMaxSize()
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
                                    text = uiState.errorMessage ?: "Errore durante la ricerca",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(16.dp)
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
                                        "Nessun utente caricato. Trascina verso il basso per cercare altri mappatori."
                                    } else {
                                        "Nessun risultato per \"${uiState.searchTerm.trim()}\""
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(16.dp)
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
                                        onClick = onUserClick
                                    )
                                }

                                item(key = "users_footer") {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // TODO: valutare in futuro se nascondere/disabilitare il bottone quando hasReachedEnd = true.
                                        if (!isSearchingByName) {
                                            Button(
                                                onClick = { viewModel.loadMoreUsers() },
                                                enabled = !uiState.isPaging
                                            ) {
                                                Text(if (uiState.isPaging) "Caricamento..." else "Carica altri utenti")
                                            }
                                        }

                                        if (uiState.hasReachedEnd) {
                                            Text(
                                                text = "fine della lista",
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
fun UserListItem(
    user: OsmUser,
    analysis: UserAnalysis,
    onClick: (OsmUser, UserAnalysis) -> Unit
) {
    Card(
        onClick = { onClick(user, analysis) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with profile image or placeholder
            ProfileAvatar(
                displayName = user.displayName,
                imageUrl = user.img?.href,
                modifier = Modifier.size(50.dp),
                contentDescription = "Foto profilo di ${user.displayName}",
                size = 50
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Edits: ${user.changesets?.count ?: 0}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "OSMcha: 👍 ${analysis.osmchaLikes} / 👎 ${analysis.osmchaDislikes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (analysis.isNewcomer) {
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    "Novizio",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            enabled = false
                        )
                    }
                    if (analysis.isReturning) {
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    "Ritornato",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            enabled = false
                        )
                    }
                    if (analysis.isWelcomed) {
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    "Benvenuto",
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
                            colors = AssistChipDefaults.assistChipColors(leadingIconContentColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }

            @Suppress("DEPRECATION")
            Icon(Icons.Default.ArrowForward, contentDescription = "Vedi dettagli")
        }
    }
}