package com.antoninofaro.welcometool.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antoninofaro.welcometool.BuildConfig
import com.antoninofaro.welcometool.R
import com.antoninofaro.welcometool.ui.components.fadingEdge
import com.antoninofaro.welcometool.ui.screens.settings.AppearanceSection
import com.antoninofaro.welcometool.ui.screens.settings.CacheSection
import com.antoninofaro.welcometool.ui.screens.settings.ClearCacheDialog
import com.antoninofaro.welcometool.ui.screens.settings.ClearNotifiedDialog
import com.antoninofaro.welcometool.ui.screens.settings.DangerZoneSection
import com.antoninofaro.welcometool.ui.screens.settings.DebugSection
import com.antoninofaro.welcometool.ui.screens.settings.MonitoringAreasSection
import com.antoninofaro.welcometool.ui.screens.settings.OsmchaSection
import com.antoninofaro.welcometool.ui.screens.settings.OtherInfoSection
import com.antoninofaro.welcometool.ui.screens.settings.RemoveTokenDialog
import com.antoninofaro.welcometool.ui.screens.settings.ResetSettingsDialog
import com.antoninofaro.welcometool.ui.screens.settings.UpdatesNotificationsSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateUp: () -> Unit,
    onNavigateToLicenses: () -> Unit
) {
    val tokenVerification by viewModel.tokenVerification.collectAsState()
    val settingsMessage by viewModel.settingsMessage.collectAsState()
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

    LaunchedEffect(settingsMessage) {
        settingsMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSettingsMessage()
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
    appVersionName: String = BuildConfig.VERSION_NAME
) {
    val settings by viewModel.settings.collectAsState()
    val nominatimResults by viewModel.nominatimResults.collectAsState()
    val isSearchingAreas by viewModel.isSearchingAreas.collectAsState()
    val areaSearchError by viewModel.areaSearchError.collectAsState()
    val tokenVerification by viewModel.tokenVerification.collectAsState()
    var expandedSections by remember { mutableStateOf(emptySet<String>()) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showRemoveTokenDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var areaQuery by remember { mutableStateOf("") }

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

            AppearanceSection(
                viewModel = viewModel,
                settings = settings,
                expandedSections = expandedSections,
                onToggle = { expandedSections = expandedSections.toggle(it) }
            )

            UpdatesNotificationsSection(
                viewModel = viewModel,
                settings = settings,
                expandedSections = expandedSections,
                onToggle = { expandedSections = expandedSections.toggle(it) }
            )

            DebugSection(
                viewModel = viewModel,
                settings = settings,
                expandedSections = expandedSections,
                onToggle = { expandedSections = expandedSections.toggle(it) }
            )

            MonitoringAreasSection(
                viewModel = viewModel,
                settings = settings,
                nominatimResults = nominatimResults,
                isSearchingAreas = isSearchingAreas,
                areaSearchError = areaSearchError,
                expandedSections = expandedSections,
                onToggle = { expandedSections = expandedSections.toggle(it) },
                areaQuery = areaQuery,
                onAreaQueryChange = { areaQuery = it }
            )

            OsmchaSection(
                viewModel = viewModel,
                settings = settings,
                tokenVerification = tokenVerification,
                expandedSections = expandedSections,
                onToggle = { expandedSections = expandedSections.toggle(it) },
                onShowRemoveTokenDialog = { showRemoveTokenDialog = true }
            )

            CacheSection(
                viewModel = viewModel,
                expandedSections = expandedSections,
                onToggle = { expandedSections = expandedSections.toggle(it) },
                onShowClearCacheDialog = { showClearCacheDialog = true }
            )

            OtherInfoSection(
                expandedSections = expandedSections,
                onToggle = { expandedSections = expandedSections.toggle(it) },
                onNavigateToLicenses = onNavigateToLicenses,
                appVersionName = appVersionName
            )

            DangerZoneSection(
                expandedSections = expandedSections,
                onToggle = { expandedSections = expandedSections.toggle(it) },
                onShowClearDialog = { showClearDialog = true },
                onShowResetDialog = { showResetDialog = true }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showClearDialog) {
        ClearNotifiedDialog(
            onDismiss = { showClearDialog = false },
            onConfirm = { viewModel.clearNotifiedUsers() }
        )
    }

    if (showResetDialog) {
        ResetSettingsDialog(
            onDismiss = { showResetDialog = false },
            onConfirm = { viewModel.resetToDefaults() }
        )
    }

    if (showRemoveTokenDialog) {
        RemoveTokenDialog(
            onDismiss = { showRemoveTokenDialog = false },
            onConfirm = { viewModel.clearOsmchaToken() }
        )
    }

    if (showClearCacheDialog) {
        ClearCacheDialog(
            onDismiss = { showClearCacheDialog = false },
            onConfirm = { viewModel.clearAllCaches() }
        )
    }
}

private fun <T> Set<T>.toggle(item: T): Set<T> =
    if (item in this) this - item else this + item