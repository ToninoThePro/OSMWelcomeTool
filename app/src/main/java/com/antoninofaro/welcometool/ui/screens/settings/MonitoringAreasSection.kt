package com.antoninofaro.welcometool.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antoninofaro.welcometool.R
import com.antoninofaro.welcometool.data.repository.AppSettings
import com.antoninofaro.welcometool.data.repository.MonitoringArea
import com.antoninofaro.welcometool.ui.screens.SettingsViewModel
import com.antoninofaro.welcometool.utils.Constants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitoringAreasSection(
    viewModel: SettingsViewModel,
    settings: AppSettings,
    nominatimResults: List<MonitoringArea>,
    isSearchingAreas: Boolean,
    areaSearchError: String?,
    expandedSections: Set<String>,
    onToggle: (String) -> Unit,
    areaQuery: String,
    onAreaQueryChange: (String) -> Unit
) {
    val isExpanded = "areas" in expandedSections
    
    ExpandableSettingsSection(
        icon = Icons.Default.LocationOn,
        title = stringResource(R.string.monitoring_areas_section),
        isExpanded = isExpanded,
        onToggle = { onToggle("areas") }
    ) {
        OutlinedTextField(
            value = areaQuery,
            onValueChange = onAreaQueryChange,
            label = { Text(stringResource(R.string.search_area_label)) },
            placeholder = { Text(stringResource(R.string.search_area_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(
                onClick = { viewModel.searchAreas(areaQuery) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                enabled = areaQuery.isNotBlank() && !isSearchingAreas,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.search_btn))
            }

            FilledTonalButton(
                onClick = {
                    onAreaQueryChange("")
                    viewModel.clearAreaSearchResults()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.clear_btn))
            }
        }

        if (isSearchingAreas) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        }

        areaSearchError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        if (nominatimResults.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.search_results_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            nominatimResults.forEach { area ->
                SearchResultAreaItem(
                    area = area,
                    onAdd = { viewModel.addMonitoringArea(area) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.saved_areas_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

        var expandedBBox by rememberSaveable { mutableStateOf<String?>(null) }

        settings.monitoringAreas.forEach { area ->
            SavedAreaItem(
                area = area,
                isDefault = settings.defaultBBox == area.bbox,
                canRemove = settings.monitoringAreas.size > 1,
                isExpanded = expandedBBox == area.bbox,
                onToggleExpand = {
                    expandedBBox = if (expandedBBox == area.bbox) null else area.bbox
                },
                onSetDefault = { viewModel.setDefaultMonitoringArea(area) },
                onRemove = { viewModel.removeMonitoringArea(area.bbox) }
            )
        }
    }
}