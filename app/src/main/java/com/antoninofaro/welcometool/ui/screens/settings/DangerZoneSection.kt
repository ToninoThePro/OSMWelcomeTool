package com.antoninofaro.welcometool.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antoninofaro.welcometool.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DangerZoneSection(
    expandedSections: Set<String>,
    onToggle: (String) -> Unit,
    onShowClearDialog: () -> Unit,
    onShowResetDialog: () -> Unit
) {
    val isExpanded = "danger" in expandedSections
    
    ExpandableSettingsSection(
        icon = Icons.Default.Warning,
        title = stringResource(R.string.danger_zone_section),
        isExpanded = isExpanded,
        onToggle = { onToggle("danger") },
        isDanger = true
    ) {
        FilledTonalButton(
            onClick = onShowClearDialog,
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
            onClick = onShowResetDialog,
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
}