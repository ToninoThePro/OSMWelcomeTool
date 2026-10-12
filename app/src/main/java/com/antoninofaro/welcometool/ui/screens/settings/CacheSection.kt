package com.antoninofaro.welcometool.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antoninofaro.welcometool.R
import com.antoninofaro.welcometool.data.cache.CacheCategory
import com.antoninofaro.welcometool.data.cache.CacheSizesResult
import com.antoninofaro.welcometool.ui.screens.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheSection(
    viewModel: SettingsViewModel,
    expandedSections: Set<String>,
    onToggle: (String) -> Unit
) {
    val isExpanded = "cache" in expandedSections
    
    ExpandableSettingsSection(
        icon = Icons.Default.Storage,
        title = stringResource(R.string.cache_section),
        isExpanded = isExpanded,
        onToggle = { onToggle("cache") }
    ) {
        DisposableEffect(Unit) {
            viewModel.startCacheSizeCalculation()
            onDispose {
                viewModel.cancelCacheSizeCalculation()
            }
        }

        val cacheCategories = viewModel.cacheCategories.collectAsState().value
        val cacheSizes by viewModel.cacheSizes.collectAsState()
        val isCalculatingSizes by viewModel.isCalculatingSizes.collectAsState()

        // SEZIONE 1: CACHE CHIAMATE API
        Text(
            text = stringResource(R.string.cache_api_calls_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
        )
        Text(
            text = stringResource(R.string.cache_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        cacheCategories.forEach { category ->
            val size = cacheSizes?.categorySizes?.get(category.id) ?: 0L
            val categoryDesc = when (category.id) {
                "osm_api" -> stringResource(R.string.cache_category_osm_api_desc)
                "nominatim" -> stringResource(R.string.cache_category_nominatim_desc)
                "osmcha" -> stringResource(R.string.cache_category_osmcha_desc)
                "avatar" -> stringResource(R.string.cache_category_avatar_desc)
                else -> ""
            }
            val categoryIcon = when (category.id) {
                "osm_api" -> Icons.Default.Api
                "nominatim" -> Icons.Default.Map
                "osmcha" -> Icons.Default.Api
                "avatar" -> Icons.Default.Image
                else -> Icons.Default.Storage
            }

            ListItem(
                leadingContent = {
                    Icon(
                        categoryIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                headlineContent = { Text(category.name, fontWeight = FontWeight.Medium) },
                supportingContent = { Text(categoryDesc, style = MaterialTheme.typography.bodySmall) },
                trailingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isCalculatingSizes || cacheSizes == null) {
                            Text(stringResource(R.string.calculating), style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text(formatSize(size), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                        if (size > 0) {
                            IconButton(
                                onClick = { viewModel.clearCacheCategory(category.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.clear_category_cache, category.name),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        // Totale cache eliminabile e Bottone Svuota Tutto
        val currentCacheSizes = cacheSizes
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    stringResource(R.string.total_clearable_cache),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (isCalculatingSizes || currentCacheSizes == null) {
                    Text(stringResource(R.string.calculating), style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(
                        formatSize(currentCacheSizes.totalHttpCacheSize),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            FilledTonalButton(
                onClick = { viewModel.clearAllCaches() },
                enabled = (currentCacheSizes?.totalHttpCacheSize ?: 0L) > 0L,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.clear_btn))
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        // SEZIONE 2: ELENCO ELEMENTI NON CACHE (STATO E SPAZIO APPLICAZIONE)
        Text(
            text = stringResource(R.string.non_cache_data_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Text(
            text = stringResource(R.string.non_cache_data_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Card Database Locale (Room)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.local_database),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (isCalculatingSizes || cacheSizes == null) {
                        Text(stringResource(R.string.calculating), style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(
                            formatSize(cacheSizes!!.roomDatabaseSize),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Dettagli elementi Room
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.cache_room_users),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.count_users, cacheSizes?.userCount ?: 0),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.cache_room_changesets),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.count_changesets, cacheSizes?.changesetCount ?: 0),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.cache_room_activity),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.count_activities, cacheSizes?.userAreaActivityCount ?: 0),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Card Preferenze e Registri (DataStore)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.settings_preferences),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    val currentCacheSizes = cacheSizes
                    if (isCalculatingSizes || currentCacheSizes == null) {
                        Text(stringResource(R.string.calculating), style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(
                            formatSize(currentCacheSizes.dataStoreSize),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Dettagli elementi DataStore
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.cache_datastore_welcomed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.count_welcomed, currentCacheSizes?.welcomedCount ?: 0),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun formatSize(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", sizeBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}