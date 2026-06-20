package com.antoninofaro.welcometool.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.antoninofaro.welcometool.BuildConfig
import com.antoninofaro.welcometool.data.repository.AppSettings
import com.antoninofaro.welcometool.data.repository.MonitoringArea
import com.antoninofaro.welcometool.data.repository.normalizeOsmchaToken
import com.antoninofaro.welcometool.ui.theme.WelcomeToolTheme

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
    SettingsScreenContent(
        settings = settings,
        nominatimResults = nominatimResults,
        isSearchingAreas = isSearchingAreas,
        areaSearchError = areaSearchError,
        onNavigateUp = onNavigateUp,
        onNavigateToLicenses = onNavigateToLicenses,
        onDarkModeChange = viewModel::updateDarkMode,
        onAutoRefreshChange = viewModel::updateAutoRefresh,
        onAutoRefreshIntervalChange = viewModel::updateAutoRefreshInterval,
        onMinChangesetsFilterChange = viewModel::updateMinChangesetsFilter,
        onSearchAreas = viewModel::searchAreas,
        onClearAreaSearchResults = viewModel::clearAreaSearchResults,
        onAddMonitoringArea = viewModel::addMonitoringArea,
        onSetDefaultMonitoringArea = viewModel::setDefaultMonitoringArea,
        onRemoveMonitoringArea = viewModel::removeMonitoringArea,
        onShowNotificationsChange = viewModel::updateShowNotifications,
        onClearNotifiedUsers = viewModel::clearNotifiedUsers,
        onCacheEnabledChange = viewModel::updateCacheEnabled,
        onUpdateOsmchaToken = viewModel::updateOsmchaToken,
        onOsmchaChangesetsLimitChange = viewModel::updateOsmchaChangesetsLimit,
        onDebugLogsEnabledChange = viewModel::updateDebugLogsEnabled,
        onResetToDefaults = viewModel::resetToDefaults
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreenContent(
    settings: AppSettings,
    nominatimResults: List<MonitoringArea>,
    isSearchingAreas: Boolean,
    areaSearchError: String?,
    onNavigateUp: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    onAutoRefreshChange: (Boolean) -> Unit,
    onAutoRefreshIntervalChange: (Int) -> Unit,
    onMinChangesetsFilterChange: (Int) -> Unit,
    onSearchAreas: (String) -> Unit,
    onClearAreaSearchResults: () -> Unit,
    onAddMonitoringArea: (MonitoringArea) -> Unit,
    onSetDefaultMonitoringArea: (MonitoringArea) -> Unit,
    onRemoveMonitoringArea: (String) -> Unit,
    onShowNotificationsChange: (Boolean) -> Unit,
    onClearNotifiedUsers: () -> Unit,
    onCacheEnabledChange: (Boolean) -> Unit,
    onUpdateOsmchaToken: (String) -> Unit,
    onOsmchaChangesetsLimitChange: (Int) -> Unit,
    onDebugLogsEnabledChange: (Boolean) -> Unit = {},
    onResetToDefaults: () -> Unit,
    showDebugTokenStatus: Boolean = BuildConfig.DEBUG,
    appVersionName: String = BuildConfig.VERSION_NAME
) {
    var showResetDialog by remember { mutableStateOf(false) }
    var areaQuery by remember { mutableStateOf("") }
    var osmchaTokenInput by rememberSaveable(settings.osmchaToken) { mutableStateOf(settings.osmchaToken) }
    var showOsmchaToken by remember { mutableStateOf(false) }
    val normalizedTokenInput = remember(osmchaTokenInput) { normalizeOsmchaToken(osmchaTokenInput) }
    val tokenLength = normalizedTokenInput.length
    val isTokenLengthValid = normalizedTokenInput.isBlank() || tokenLength == OSMCHA_TOKEN_EXPECTED_LENGTH

    val tokenStatusText = when {
        settings.osmchaToken.isNotBlank() -> "Token salvato (impostazioni)"
        showDebugTokenStatus -> "Token debug attivo (BuildConfig)"
        else -> "Nessun token OSMcha configurato"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Sezione Aspetto
            SettingsSectionHeader(title = "ASPETTO")

            SettingsToggleItem(
                icon = Icons.Default.DarkMode,
                title = "Tema Scuro",
                description = "Attiva il tema scuro dell'interfaccia",
                checked = settings.darkMode,
                onCheckedChange = onDarkModeChange
            )

            HorizontalDivider()

            // Sezione Aggiornamento
            SettingsSectionHeader(title = "AGGIORNAMENTO DATI")

            SettingsToggleItem(
                icon = Icons.Default.Refresh,
                title = "Aggiornamento Automatico",
                description = "Aggiorna automaticamente la lista degli utenti",
                checked = settings.autoRefresh,
                onCheckedChange = onAutoRefreshChange
            )

            if (settings.autoRefresh) {
                SettingsSliderItem(
                    title = "Intervallo di Aggiornamento",
                    description = "${settings.autoRefreshInterval} minuti",
                    value = settings.autoRefreshInterval.toFloat(),
                    valueRange = 5f..120f,
                    steps = 22,
                    onValueChange = { onAutoRefreshIntervalChange(it.toInt()) }
                )
            }

            HorizontalDivider()

            // Sezione Filtri
            SettingsSectionHeader(title = "FILTRI PREDEFINITI")

            SettingsSliderItem(
                title = "Modifiche Minime",
                description = "Mostra solo utenti con almeno ${settings.minChangesetsFilter} modifiche",
                value = settings.minChangesetsFilter.toFloat(),
                valueRange = 0f..500f,
                steps = 49,
                onValueChange = { onMinChangesetsFilterChange(it.toInt()) }
            )

            HorizontalDivider()

            // Sezione Aree di Controllo
            SettingsSectionHeader(title = "AREE DI CONTROLLO")

            OutlinedTextField(
                value = areaQuery,
                onValueChange = { areaQuery = it },
                label = { Text("Cerca area con Nominatim") },
                placeholder = { Text("Es: Palermo, Catania, Etna") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onSearchAreas(areaQuery) },
                    modifier = Modifier.weight(1f),
                    enabled = areaQuery.isNotBlank() && !isSearchingAreas
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cerca")
                }

                OutlinedButton(
                    onClick = {
                        areaQuery = ""
                        onClearAreaSearchResults()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Pulisci")
                }
            }

            if (isSearchingAreas) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (nominatimResults.isNotEmpty()) {
                Text(
                    text = "Risultati ricerca",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                nominatimResults.forEach { area ->
                    SearchResultAreaItem(
                        area = area,
                        onAdd = { onAddMonitoringArea(area) }
                    )
                }
            }

            Text(
                text = "Aree salvate",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            settings.monitoringAreas.forEach { area ->
                SavedAreaItem(
                    area = area,
                    isDefault = settings.defaultBBox == area.bbox,
                    canRemove = settings.monitoringAreas.size > 1,
                    onSetDefault = { onSetDefaultMonitoringArea(area) },
                    onRemove = { onRemoveMonitoringArea(area.bbox) }
                )
            }

            HorizontalDivider()

            // Sezione Notifiche
            SettingsSectionHeader(title = "NOTIFICHE")

            SettingsToggleItem(
                icon = Icons.Default.Notifications,
                title = "Notifiche",
                description = "Ricevi notifiche per nuovi mappatori",
                checked = settings.showNotifications,
                onCheckedChange = onShowNotificationsChange
            )

            // Pulsante per cancellare utenti notificati
            OutlinedButton(
                onClick = onClearNotifiedUsers,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cancella Lista Utenti Notificati")
            }

            Text(
                text = "Cancella la cronologia degli utenti già notificati. Riceverai di nuovo notifiche per questi utenti.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            HorizontalDivider()

            // Sezione Cache e Performance
            SettingsSectionHeader(title = "CACHE E PERFORMANCE")

            SettingsToggleItem(
                icon = Icons.Default.Storage,
                title = "Cache Attiva",
                description = "Salva dati in cache per velocizzare il caricamento",
                checked = settings.cacheEnabled,
                onCheckedChange = onCacheEnabledChange
            )

            HorizontalDivider()
            // Sezione OSMcha
            SettingsSectionHeader(title = "OSMCHA")

            OutlinedTextField(
                value = osmchaTokenInput,
                onValueChange = { rawInput ->
                    // Handle paste from OSMCha UI where token is often copied as "Token <value>".
                    osmchaTokenInput = normalizeOsmchaToken(rawInput)
                },
                label = { Text("Token OSMcha") },
                placeholder = { Text("Inserisci token") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true,
                isError = normalizedTokenInput.isNotBlank() && !isTokenLengthValid,
                visualTransformation = if (showOsmchaToken) VisualTransformation.None else PasswordVisualTransformation(),
                supportingText = {
                    if (normalizedTokenInput.isBlank()) {
                        Text("Lunghezza attesa: $OSMCHA_TOKEN_EXPECTED_LENGTH caratteri")
                    } else {
                        val prefix = if (isTokenLengthValid) "Lunghezza token" else "Token non valido"
                        Text("$prefix: $tokenLength/$OSMCHA_TOKEN_EXPECTED_LENGTH")
                    }
                },
                trailingIcon = {
                    IconButton(onClick = { showOsmchaToken = !showOsmchaToken }) {
                        val icon =
                            if (showOsmchaToken) Icons.Default.VisibilityOff else Icons.Default.Visibility
                        val desc = if (showOsmchaToken) "Nascondi token" else "Mostra token"
                        Icon(icon, contentDescription = desc)
                    }
                }
            )

            Text(
                text = tokenStatusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onUpdateOsmchaToken(normalizedTokenInput) },
                    modifier = Modifier.weight(1f),
                    enabled = normalizedTokenInput.isNotBlank() && isTokenLengthValid
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Salva")
                }
                OutlinedButton(
                    onClick = {
                        osmchaTokenInput = ""
                        onUpdateOsmchaToken("")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Rimuovi")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSliderItem(
                title = "Changeset da Controllare",
                description = "${settings.osmchaChangesetsLimit} changeset",
                value = settings.osmchaChangesetsLimit.toFloat(),
                valueRange = 10f..500f,
                steps = 49,
                onValueChange = { onOsmchaChangesetsLimitChange(it.toInt()) }
            )

            HorizontalDivider()

            // Informazioni App
            SettingsSectionHeader(title = "INFORMAZIONI")

            SettingsInfoItem(
                icon = Icons.Default.Info,
                title = "Versione App",
                description = appVersionName
            )

            SettingsInfoItem(
                icon = Icons.Default.LocationOn,
                title = "Area Predefinita",
                description = "${settings.defaultAreaName} (${settings.defaultBBox})"
            )

            ListItem(
                headlineContent = { Text("Licenze") },
                supportingContent = { Text("Librerie e licenze utilizzate") },
                leadingContent = {
                    Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier.clickable(onClick = onNavigateToLicenses)
            )

            HorizontalDivider()

            SettingsSectionHeader(title = "LOG E DIAGNOSTICA")

            SettingsToggleItem(
                icon = Icons.Default.Info,
                title = "Cattura Log",
                description = "Attiva la cattura dei log Timber (riduce performance)",
                checked = settings.debugLogsEnabled,
                onCheckedChange = onDebugLogsEnabledChange
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Pulsante Reset
            OutlinedButton(
                onClick = { showResetDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ripristina Impostazioni Predefinite")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Dialog di conferma reset
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Ripristina Impostazioni") },
            text = { Text("Sei sicuro di voler ripristinare tutte le impostazioni ai valori predefiniti?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetToDefaults()
                        showResetDialog = false
                    }
                ) {
                    Text("Ripristina")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Annulla")
                }
            }
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SettingsScreenPreview() {
    val previewSettings = AppSettings(
        darkMode = true,
        autoRefresh = true,
        autoRefreshInterval = 30,
        defaultBBox = "12.35,37.35,15.70,38.90",
        defaultAreaName = "Sicilia",
        showNotifications = true,
        minChangesetsFilter = 25,
        cacheEnabled = true,
        osmchaToken = "preview-token",
        osmchaChangesetsLimit = 150,
        monitoringAreas = listOf(
            MonitoringArea("Sicilia", "12.35,37.35,15.70,38.90"),
            MonitoringArea("Palermo", "13.20,38.05,13.50,38.25")
        )
    )

    val previewSearchResults = listOf(
        MonitoringArea("Catania", "14.95,37.40,15.25,37.65"),
        MonitoringArea("Etna", "14.95,37.60,15.30,37.90")
    )

    WelcomeToolTheme {
        SettingsScreenContent(
            settings = previewSettings,
            nominatimResults = previewSearchResults,
            isSearchingAreas = false,
            areaSearchError = null,
            onNavigateUp = {},
            onNavigateToLicenses = {},
            onDarkModeChange = {},
            onAutoRefreshChange = {},
            onAutoRefreshIntervalChange = {},
            onMinChangesetsFilterChange = {},
            onSearchAreas = {},
            onClearAreaSearchResults = {},
            onAddMonitoringArea = {},
            onSetDefaultMonitoringArea = {},
            onRemoveMonitoringArea = {},
            onShowNotificationsChange = {},
            onClearNotifiedUsers = {},
            onCacheEnabledChange = {},
            onUpdateOsmchaToken = {},
            onOsmchaChangesetsLimitChange = {},
            onDebugLogsEnabledChange = {},
            onResetToDefaults = {},
            showDebugTokenStatus = true,
            appVersionName = "0.0.0-preview"
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
            Icon(Icons.Default.LocationOn, contentDescription = null)
        },
        headlineContent = { Text(area.name) },
        supportingContent = { Text(area.bbox, style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Aggiungi area")
            }
        }
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
                tint = if (isDefault) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        headlineContent = { Text(area.name) },
        supportingContent = { Text(area.bbox, style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!isDefault) {
                    TextButton(onClick = onSetDefault) {
                        Text("Imposta")
                    }
                }
                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Delete, contentDescription = "Rimuovi area")
                    }
                }
            }
        }
    )
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
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
        headlineContent = { Text(title) },
        supportingContent = { Text(description, style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    )
}

@Composable
fun SettingsSliderItem(
    title: String,
    description: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}

@Composable
fun SettingsInfoItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    ListItem(
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        headlineContent = { Text(title) },
        supportingContent = { Text(description, style = MaterialTheme.typography.bodySmall) }
    )
}

