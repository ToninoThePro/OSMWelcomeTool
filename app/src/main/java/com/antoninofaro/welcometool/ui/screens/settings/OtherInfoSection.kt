package com.antoninofaro.welcometool.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antoninofaro.welcometool.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtherInfoSection(
    expandedSections: Set<String>,
    onToggle: (String) -> Unit,
    onNavigateToLicenses: () -> Unit,
    appVersionName: String
) {
    val isExpanded = "info" in expandedSections
    
    ExpandableSettingsSection(
        icon = Icons.Default.Info,
        title = stringResource(R.string.other_info_section),
        isExpanded = isExpanded,
        onToggle = { onToggle("info") }
    ) {
        SettingsInfoItem(
            icon = Icons.Default.Info,
            title = stringResource(R.string.app_version),
            description = appVersionName
        )

        Spacer(modifier = Modifier.height(8.dp))

        SettingsInfoItem(
            icon = Icons.Default.Language,
            title = stringResource(R.string.api_requests_title),
            description = "OSM, Nominatim, OSMCha"
        )

        Spacer(modifier = Modifier.height(8.dp))

        ListItem(
            headlineContent = {
                Text(
                    stringResource(R.string.licenses_btn),
                    fontWeight = FontWeight.Medium
                )
            },
            supportingContent = { Text(stringResource(R.string.licenses_desc)) },
            leadingContent = {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToLicenses)
                .padding(vertical = 4.dp)
        )
    }
}