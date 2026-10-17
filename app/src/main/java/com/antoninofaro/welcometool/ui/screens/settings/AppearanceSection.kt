package com.antoninofaro.welcometool.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antoninofaro.welcometool.R
import com.antoninofaro.welcometool.data.repository.AppSettings
import com.antoninofaro.welcometool.ui.screens.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSection(
    viewModel: SettingsViewModel,
    settings: AppSettings,
    expandedSections: Set<String>,
    onToggle: (String) -> Unit
) {
    val isExpanded = "appearance" in expandedSections
    
    ExpandableSettingsSection(
        icon = Icons.Default.Palette,
        title = stringResource(R.string.appearance_section),
        isExpanded = isExpanded,
        onToggle = { onToggle("appearance") }
    ) {
        Text(
            text = stringResource(R.string.theme_mode_title),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            listOf("light", "system", "dark").forEach { mode ->
                FilterChip(
                    selected = settings.themeMode == mode,
                    onClick = { viewModel.updateThemeMode(mode) },
                    label = {
                        Text(
                            stringResource(
                                when (mode) {
                                    "light" -> R.string.theme_mode_light
                                    "dark" -> R.string.theme_mode_dark
                                    else -> R.string.theme_mode_system
                                }
                            )
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        SettingsToggleItem(
            icon = Icons.Default.Palette,
            title = stringResource(R.string.dynamic_color_title),
            description = stringResource(R.string.dynamic_color_desc),
            checked = settings.dynamicColor,
            onCheckedChange = viewModel::updateDynamicColor
        )
    }
}