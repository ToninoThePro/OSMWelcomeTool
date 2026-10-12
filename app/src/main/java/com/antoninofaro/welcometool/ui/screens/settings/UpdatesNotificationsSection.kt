package com.antoninofaro.welcometool.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antoninofaro.welcometool.R
import com.antoninofaro.welcometool.data.repository.AppSettings
import com.antoninofaro.welcometool.ui.screens.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesNotificationsSection(
    viewModel: SettingsViewModel,
    settings: AppSettings,
    expandedSections: Set<String>,
    onToggle: (String) -> Unit
) {
    val isExpanded = "updates" in expandedSections
    val context = LocalContext.current
    
    ExpandableSettingsSection(
        icon = Icons.Default.Refresh,
        title = stringResource(R.string.updates_notifications_section),
        isExpanded = isExpanded,
        onToggle = { onToggle("updates") }
    ) {
        SettingsToggleItem(
            icon = Icons.Default.Refresh,
            title = stringResource(R.string.auto_refresh_title),
            description = stringResource(R.string.auto_refresh_desc),
            checked = settings.autoRefresh,
            onCheckedChange = viewModel::updateAutoRefresh
        )

        if (settings.autoRefresh) {
            Spacer(modifier = Modifier.height(16.dp))
            var intervalText by remember(settings.autoRefreshInterval) {
                mutableStateOf(
                    settings.autoRefreshInterval.toString()
                )
            }
            val interval = intervalText.toIntOrNull()
            ValidatedSettingsTextField(
                value = intervalText,
                onValueChange = { text ->
                    intervalText = text
                    text.toIntOrNull()
                        ?.let { v -> if (v in 15..120) viewModel.updateAutoRefreshInterval(v) }
                },
                label = { Text(stringResource(R.string.refresh_interval_label)) },
                isError = interval?.let { it !in 15..120 } ?: (intervalText.isNotEmpty()),
                supportingText = {
                    Text(interval?.let {
                        if (it in 15..120) stringResource(
                            R.string.interval_minutes,
                            it
                        ) else stringResource(R.string.min_interval_hint)
                    } ?: stringResource(R.string.min_interval_hint))
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = { viewModel.triggerManualSync(context) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.sync_now_btn))
        }

        Spacer(modifier = Modifier.height(12.dp))

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                viewModel.updateShowNotifications(true)
            }
        }

        SettingsToggleItem(
            icon = Icons.Default.Notifications,
            title = stringResource(R.string.new_mappers_notif_title),
            description = stringResource(R.string.new_mappers_notif_desc),
            checked = settings.showNotifications,
            onCheckedChange = { enabled ->
                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        return@SettingsToggleItem
                    }
                }
                viewModel.updateShowNotifications(enabled)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        SettingsToggleItem(
            icon = Icons.Default.Notifications,
            title = stringResource(R.string.new_changesets_notif_title),
            description = stringResource(R.string.new_changesets_notif_desc),
            checked = settings.showNewChangesetNotifications,
            onCheckedChange = { enabled ->
                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        return@SettingsToggleItem
                    }
                }
                viewModel.updateShowNewChangesetNotifications(enabled)
            }
        )

        androidx.compose.animation.AnimatedVisibility(visible = settings.showNewChangesetNotifications) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                SettingsToggleItem(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.individual_changeset_notif_title),
                    description = stringResource(R.string.individual_changeset_notif_desc),
                    checked = settings.individualChangesetNotifications,
                    onCheckedChange = { enabled ->
                        viewModel.updateIndividualChangesetNotifications(enabled)
                    }
                )
            }
        }

        if (settings.showNotifications || settings.showNewChangesetNotifications) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.notification_auto_dismiss_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            val options = listOf(0, 1, 6, 12, 24, 48)
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(options.size) { index ->
                    val hours = options[index]
                    val label = when (hours) {
                        0 -> stringResource(R.string.notification_auto_dismiss_never)
                        1 -> stringResource(R.string.notification_auto_dismiss_hour)
                        else -> stringResource(R.string.notification_auto_dismiss_hours, hours)
                    }
                    FilterChip(
                        selected = settings.notificationAutoDismissHours == hours,
                        onClick = { viewModel.updateNotificationAutoDismissHours(hours) },
                        label = { Text(label) }
                    )
                }
            }
        }
    }
}