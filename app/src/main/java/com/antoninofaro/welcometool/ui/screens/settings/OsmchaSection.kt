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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.antoninofaro.welcometool.BuildConfig
import com.antoninofaro.welcometool.R
import com.antoninofaro.welcometool.data.repository.AppSettings
import com.antoninofaro.welcometool.data.repository.normalizeOsmchaToken
import com.antoninofaro.welcometool.ui.screens.SettingsViewModel
import com.antoninofaro.welcometool.ui.screens.TokenVerificationState

private const val OSMCHA_TOKEN_EXPECTED_LENGTH = 40

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OsmchaSection(
    viewModel: SettingsViewModel,
    settings: AppSettings,
    tokenVerification: TokenVerificationState,
    expandedSections: Set<String>,
    onToggle: (String) -> Unit,
    onShowRemoveTokenDialog: () -> Unit
) {
    val isExpanded = "osmcha" in expandedSections
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

    ExpandableSettingsSection(
        icon = Icons.Default.VpnKey,
        title = stringResource(R.string.osmcha_section),
        isExpanded = isExpanded,
        onToggle = { onToggle("osmcha") }
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
                    onClick = onShowRemoveTokenDialog,
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
}