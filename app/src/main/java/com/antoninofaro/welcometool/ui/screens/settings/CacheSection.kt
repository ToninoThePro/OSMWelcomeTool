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
import androidx.compose.ui.platform.LocalContext
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

        val cacheSizes by viewModel.cacheSizes.collectAsState()
        val isCalculatingSizes by viewModel.isCalculatingSizes.collectAsState()

        // SEZIONE CACHE
        Text(
            text = stringResource(R.string.cache_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

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
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.cache_room_users),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (isCalculatingSizes || currentCacheSizes == null) "..." else stringResource(R.string.count_users, currentCacheSizes.userCount),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.cache_datastore_welcomed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (isCalculatingSizes || currentCacheSizes == null) "..." else stringResource(R.string.count_welcomed, currentCacheSizes.welcomedCount),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        val context = LocalContext.current
        HorizontalDivider(
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = { viewModel.exportWelcomedUsers(context, SettingsViewModel.ExportFormat.CSV) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                enabled = (currentCacheSizes?.welcomedCount ?: 0) > 0
            ) {
                Text(stringResource(R.string.export_welcomed_csv))
            }
            FilledTonalButton(
                onClick = { viewModel.exportWelcomedUsers(context, SettingsViewModel.ExportFormat.JSON) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                enabled = (currentCacheSizes?.welcomedCount ?: 0) > 0
            ) {
                Text(stringResource(R.string.export_welcomed_json))
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