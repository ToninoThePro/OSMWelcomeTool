package com.antoninofaro.welcometool.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.antoninofaro.welcometool.BuildConfig
import com.antoninofaro.welcometool.R
import com.antoninofaro.welcometool.data.repository.AppSettings
import com.antoninofaro.welcometool.ui.screens.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSection(
    viewModel: SettingsViewModel,
    settings: AppSettings,
    expandedSections: Set<String>,
    onToggle: (String) -> Unit
) {
    if (!BuildConfig.DEBUG) return
    
    val isExpanded = "debug" in expandedSections
    val context = LocalContext.current
    
    ExpandableSettingsSection(
        icon = Icons.Default.BugReport,
        title = stringResource(R.string.debug_section),
        isExpanded = isExpanded,
        onToggle = { onToggle("debug") }
    ) {
        SettingsInfoItem(
            icon = Icons.Default.BugReport,
            title = stringResource(R.string.debug_changeset_title),
            description = stringResource(
                R.string.debug_changeset_id,
                settings.lastKnownChangesetId
            )
        )
        Text(
            text = stringResource(
                R.string.debug_changeset_date,
                settings.lastKnownChangesetDate.ifBlank { "N/A" }),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 56.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        FilledTonalButton(
            onClick = { viewModel.triggerManualSync(context) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.sync_now_btn))
        }

        Spacer(modifier = Modifier.height(12.dp))

        FilledTonalButton(
            onClick = { viewModel.sendTestNotification() },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Notifications, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.send_test_notif))
        }
    }
}