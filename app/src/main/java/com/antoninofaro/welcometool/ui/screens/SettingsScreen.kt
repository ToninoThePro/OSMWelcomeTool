package com.antoninofaro.welcometool.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.antoninofaro.welcometool.BuildConfig
import com.antoninofaro.welcometool.R
import com.antoninofaro.welcometool.data.repository.MonitoringArea
import com.antoninofaro.welcometool.data.repository.normalizeOsmchaToken
import com.antoninofaro.welcometool.ui.components.fadingEdge
import com.antoninofaro.welcometool.utils.Constants

private const val OSMCHA_TOKEN_EXPECTED_LENGTH = 40

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateUp: () -> Unit,
    onNavigateToLicenses: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val nominatimResults by viewModel.nominatimResults.collectAsState()
    val isSearchingAreas by viewModel.isSearchingAreas.collectAsState()
    val areaSearchError by viewModel.areaSearchError.collectAsState()
    val tokenVerification by viewModel.tokenVerification.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(tokenVerification) {
        when (val state = tokenVerification) {
            is TokenVerificationState.Success -> {
                snackbarHostState.showSnackbar(viewModel.getTokenVerifiedMessage(state.username))
            }

            is TokenVerificationState.Error -> {
                snackbarHostState.showSnackbar(state.message)
            }

            else -> {}
        }
    }

    SettingsScreenContent(
        viewModel = viewModel,
        snackbarHostState = snackbarHostState,
        onNavigateUp = onNavigateUp,
        onNavigateToLicenses = onNavigateToLicenses
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreenContent(
    viewModel: SettingsViewModel,
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    onNavigateUp: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    showDebugTokenStatus: Boolean = BuildConfig.DEBUG,
    appVersionName: String = BuildConfig.VERSION_NAME
) {
    val settings by viewModel.settings.collectAsState()
    val nominatimResults by viewModel.nominatimResults.collectAsState()
    val isSearchingAreas by viewModel.isSearchingAreas.collectAsState()
    val areaSearchError by viewModel.areaSearchError.collectAsState()
    val tokenVerification by viewModel.tokenVerification.collectAsState()
    var expandedSections by remember {
        mutableStateOf(
            setOf(
                "appearance",
                "updates",
                "areas",
                "osmcha",
                "cache_info",
                "danger"
            )
        )
    }
    var showClearDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showRemoveTokenDialog by remember { mutableStateOf(false) }
    var areaQuery by remember { mutableStateOf("") }
    var osmchaTokenInput by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(settings.osmchaToken) {
        if (settings.osmchaToken.isBlank()) osmchaTokenInput = ""
    }
    var showOsmchaToken by remember { mutableStateOf(false) }
    val normalizedTokenInput = remember(osmchaTokenInput) { normalizeOsmchaToken(osmchaTokenInput) }
    val tokenLength = normalizedTokenInput.length
    val isTokenLengthValid =
        normalizedTokenInput.isBlank() || tokenLength == OSMCHA_TOKEN_EXPECTED_LENGTH
    val isVerifying = tokenVerification is TokenVerificationState.Verifying

    val hasTokenSaved = settings.osmchaToken.isNotBlank()
    val hasVerifiedUsername = settings.verifiedOsmchaUsername.isNotBlank()
    val tokenLast4 = remember(settings.osmchaToken) {
        if (settings.osmchaToken.length >= 4) settings.osmchaToken.takeLast(4) else ""
    }

    if ("debug" !in expandedSections && BuildConfig.DEBUG) {
        expandedSections = expandedSections + "debug"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_desc)
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
                .fadingEdge(40.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ASPETTO
            ExpandableSettingsSection(
                icon = Icons.Default.Palette,
                title = stringResource(R.string.appearance_section),
                isExpanded = "appearance" in expandedSections,
                onToggle = { expandedSections = expandedSections.toggle("appearance") }
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

            // AGGIORNAMENTI E NOTIFICHE
            ExpandableSettingsSection(
                icon = Icons.Default.Refresh,
                title = stringResource(R.string.updates_notifications_section),
                isExpanded = "updates" in expandedSections,
                onToggle = { expandedSections = expandedSections.toggle("updates") }
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

                Spacer(modifier = Modifier.height(12.dp))

                val context = LocalContext.current
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
            }

            // DEBUG INFO
            if (BuildConfig.DEBUG) {
                ExpandableSettingsSection(
                    icon = Icons.Default.BugReport,
                    title = stringResource(R.string.debug_section),
                    isExpanded = "debug" in expandedSections,
                    onToggle = { expandedSections = expandedSections.toggle("debug") }
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

            // AREE DI MONITORAGGIO
            ExpandableSettingsSection(
                icon = Icons.Default.LocationOn,
                title = stringResource(R.string.monitoring_areas_section),
                isExpanded = "areas" in expandedSections,
                onToggle = { expandedSections = expandedSections.toggle("areas") }
            ) {
                OutlinedTextField(
                    value = areaQuery,
                    onValueChange = { areaQuery = it },
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
                            areaQuery = ""
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

                val visibleAreas = remember(settings.monitoringAreas, settings.defaultBBox) {
                    val areas = settings.monitoringAreas
                    if (areas.size > 1) {
                        areas.filter { area ->
                            area.bbox != Constants.ITALY_BBOX || settings.defaultBBox == Constants.ITALY_BBOX
                        }
                    } else {
                        areas
                    }
                }

                visibleAreas.forEach { area ->
                    SavedAreaItem(
                        area = area,
                        isDefault = settings.defaultBBox == area.bbox,
                        canRemove = settings.monitoringAreas.size > 1,
                        onSetDefault = { viewModel.setDefaultMonitoringArea(area) },
                        onRemove = { viewModel.removeMonitoringArea(area.bbox) }
                    )
                }
            }

            // OSMCha
            ExpandableSettingsSection(
                icon = Icons.Default.VpnKey,
                title = stringResource(R.string.osmcha_section),
                isExpanded = "osmcha" in expandedSections,
                onToggle = { expandedSections = expandedSections.toggle("osmcha") }
            ) {
                if (!hasTokenSaved) {
                    OutlinedTextField(
                        value = osmchaTokenInput,
                        onValueChange = { rawInput ->
                            osmchaTokenInput = normalizeOsmchaToken(rawInput)
                        },
                        label = { Text(stringResource(R.string.osmcha_token_label)) },
                        placeholder = { Text(stringResource(R.string.osmcha_token_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            autoCorrectEnabled = false
                        ),
                        isError = normalizedTokenInput.isNotBlank() && !isTokenLengthValid,
                        visualTransformation = if (showOsmchaToken) VisualTransformation.None else PasswordVisualTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        supportingText = {
                            if (normalizedTokenInput.isBlank()) {
                                Text(
                                    stringResource(
                                        R.string.osmcha_expected_length,
                                        OSMCHA_TOKEN_EXPECTED_LENGTH
                                    )
                                )
                            } else {
                                val prefix =
                                    if (isTokenLengthValid) stringResource(R.string.osmcha_token_length_label) else stringResource(
                                        R.string.osmcha_token_invalid_label
                                    )
                                Text(
                                    stringResource(
                                        R.string.osmcha_token_status_format,
                                        prefix,
                                        tokenLength,
                                        OSMCHA_TOKEN_EXPECTED_LENGTH
                                    )
                                )
                            }
                        },
                        trailingIcon = {
                            IconButton(onClick = { showOsmchaToken = !showOsmchaToken }) {
                                val icon =
                                    if (showOsmchaToken) Icons.Default.VisibilityOff else Icons.Default.Visibility
                                val desc =
                                    if (showOsmchaToken) stringResource(R.string.hide_token) else stringResource(
                                        R.string.show_token
                                    )
                                Icon(icon, contentDescription = desc)
                            }
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.updateOsmchaToken(normalizedTokenInput) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            enabled = normalizedTokenInput.isNotBlank() && isTokenLengthValid,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.save_btn))
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        if (hasVerifiedUsername) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Column {
                                Text(
                                    stringResource(
                                        R.string.osmcha_connected_as,
                                        settings.verifiedOsmchaUsername
                                    ),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                if (tokenLast4.isNotBlank()) {
                                    Text(
                                        stringResource(R.string.osmcha_token_masked, tokenLast4),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            Text(
                                stringResource(R.string.osmcha_token_saved_unverified),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                        FilledTonalButton(
                            onClick = { viewModel.reverifyOsmchaToken() },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            enabled = !isVerifying,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.verify_btn))
                        }
                        FilledTonalButton(
                            onClick = { showRemoveTokenDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.remove_btn))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                var changesetLimitText by remember(settings.osmchaChangesetsLimit) {
                    mutableStateOf(
                        settings.osmchaChangesetsLimit.toString()
                    )
                }
                val changesetLimit = changesetLimitText.toIntOrNull()
                ValidatedSettingsTextField(
                    value = changesetLimitText,
                    onValueChange = { text ->
                        changesetLimitText = text
                        text.toIntOrNull()
                            ?.let { v -> if (v in 1..500) viewModel.updateOsmchaChangesetsLimit(v) }
                    },
                    label = { Text(stringResource(R.string.changesets_to_check)) },
                    isError = changesetLimit?.let { it !in 1..500 }
                        ?: (changesetLimitText.isNotEmpty()),
                    supportingText = {
                        Text(changesetLimit?.let {
                            if (it in 1..500) stringResource(
                                R.string.changeset_count,
                                it
                            ) else stringResource(R.string.changeset_limit_hint)
                        } ?: stringResource(R.string.changeset_limit_hint))
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                var autoRefreshDaysText by remember(settings.osmchaAutoRefreshDays) {
                    mutableStateOf(
                        settings.osmchaAutoRefreshDays.toString()
                    )
                }
                val autoRefreshDays = autoRefreshDaysText.toIntOrNull()
                ValidatedSettingsTextField(
                    value = autoRefreshDaysText,
                    onValueChange = { text ->
                        autoRefreshDaysText = text
                        text.toIntOrNull()
                            ?.let { v -> if (v in 1..30) viewModel.updateOsmchaAutoRefreshDays(v) }
                    },
                    label = { Text(stringResource(R.string.osmcha_auto_refresh_label)) },
                    isError = autoRefreshDays?.let { it !in 1..30 }
                        ?: (autoRefreshDaysText.isNotEmpty()),
                    supportingText = {
                        Text(autoRefreshDays?.let {
                            if (it in 1..30) stringResource(
                                R.string.osmcha_auto_days,
                                it
                            ) else stringResource(R.string.osmcha_range_hint)
                        } ?: stringResource(R.string.osmcha_range_hint))
                    }
                )
            }

            // CACHE E INFORMAZIONI
            ExpandableSettingsSection(
                icon = Icons.Default.Storage,
                title = stringResource(R.string.cache_info_section),
                isExpanded = "cache_info" in expandedSections,
                onToggle = { expandedSections = expandedSections.toggle("cache_info") }
            ) {
                SettingsToggleItem(
                    icon = Icons.Default.Storage,
                    title = stringResource(R.string.cache_title),
                    description = stringResource(R.string.cache_desc),
                    checked = settings.cacheEnabled,
                    onCheckedChange = viewModel::updateCacheEnabled
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                SettingsInfoItem(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.app_version),
                    description = appVersionName
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingsInfoItem(
                    icon = Icons.Default.LocationOn,
                    title = stringResource(R.string.default_area),
                    description = "${settings.defaultAreaName} (${settings.defaultBBox})"
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
                    modifier = Modifier.clickable(onClick = onNavigateToLicenses)
                )
            }

            // ZONA PERICOLOSA
            ExpandableSettingsSection(
                icon = Icons.Default.Warning,
                title = stringResource(R.string.danger_zone_section),
                isExpanded = "danger" in expandedSections,
                onToggle = { expandedSections = expandedSections.toggle("danger") },
                isDanger = true
            ) {
                FilledTonalButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.clear_notified_btn), fontWeight = FontWeight.Bold)
                }

                Text(
                    text = stringResource(R.string.clear_notified_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                )

                FilledTonalButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.reset_settings_btn), fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.clear_dialog_title)) },
            text = { Text(stringResource(R.string.clear_dialog_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearNotifiedUsers()
                    showClearDialog = false
                }) {
                    Text(
                        stringResource(R.string.delete_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showClearDialog = false
                }) { Text(stringResource(R.string.cancel_btn)) }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.reset_dialog_title)) },
            text = { Text(stringResource(R.string.reset_dialog_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetToDefaults()
                        showResetDialog = false
                    }
                ) {
                    Text(
                        stringResource(R.string.reset_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.cancel_btn))
                }
            }
        )
    }

    if (showRemoveTokenDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveTokenDialog = false },
            icon = {
                Icon(
                    Icons.Default.VpnKey,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.remove_token_title)) },
            text = { Text(stringResource(R.string.remove_token_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearOsmchaToken()
                        showRemoveTokenDialog = false
                    }
                ) {
                    Text(
                        stringResource(R.string.remove_btn),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveTokenDialog = false }) {
                    Text(stringResource(R.string.cancel_btn))
                }
            }
        )
    }
}

@Composable
private fun SearchResultAreaItem(
    area: MonitoringArea,
    onAdd: () -> Unit
) {
    ListItem(
        leadingContent = {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary
            )
        },
        headlineContent = { Text(area.name, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(area.bbox, style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            IconButton(onClick = onAdd) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_area_desc),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun SavedAreaItem(
    area: MonitoringArea,
    isDefault: Boolean,
    canRemove: Boolean,
    onSetDefault: () -> Unit,
    onRemove: () -> Unit
) {
    ListItem(
        leadingContent = {
            Icon(
                if (isDefault) Icons.Default.Star else Icons.Default.LocationOn,
                contentDescription = null,
                tint = if (isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )
        },
        headlineContent = {
            Text(
                area.name,
                fontWeight = if (isDefault) FontWeight.Bold else FontWeight.Medium
            )
        },
        supportingContent = { Text(area.bbox, style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!isDefault) {
                    TextButton(onClick = onSetDefault) {
                        Text(
                            stringResource(R.string.set_default),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.remove_area_desc),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun ValidatedSettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    isError: Boolean,
    supportingText: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        isError = isError,
        supportingText = supportingText,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(description, style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun SettingsInfoItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    ListItem(
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        },
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(description, style = MaterialTheme.typography.bodySmall) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

private fun <T> Set<T>.toggle(item: T): Set<T> =
    if (item in this) this - item else this + item

@Composable
private fun ExpandableSettingsSection(
    icon: ImageVector,
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    isDanger: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = if (isDanger) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ) else CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isDanger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    color = if (isDanger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = if (isDanger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
                    content()
                }
            }
        }
    }
}
