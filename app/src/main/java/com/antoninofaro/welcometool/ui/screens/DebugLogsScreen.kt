package com.antoninofaro.welcometool.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.antoninofaro.welcometool.utils.LogCaptureManager
import com.antoninofaro.welcometool.utils.LogEntry

@Suppress("UNUSED_PARAMETER", "DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogsScreen(
    logCaptureManager: LogCaptureManager,
    onNavigateBack: () -> Unit
) {
    val logs by logCaptureManager.logs.collectAsState()
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Logs") },
                actions = {
                    IconButton(
                        onClick = { logCaptureManager.clearLogs() },
                        enabled = logs.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Cancella log")
                    }
                    IconButton(
                        onClick = {
                            // In una vera app, qui esporteresti o copieremmo negli appunti
                            // Per ora, solo placeholder
                        },
                        enabled = logs.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Esporta log")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Stats Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Log entries: ${logs.size}",
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = onNavigateBack,
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Back")
                }
            }

            // Logs List
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Nessun log disponibile")
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .padding(8.dp)
                ) {
                    items(logs) { entry ->
                        LogEntryItem(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryItem(entry: LogEntry) {
    val textColor = when (entry.level) {
        "V" -> Color(0xFF888888)  // Gray
        "D" -> Color(0xFF00FF00)  // Green
        "I" -> Color(0xFF0099FF)  // Blue
        "W" -> Color(0xFFFFFF00)  // Yellow
        "E" -> Color(0xFFFF0000)  // Red
        "A" -> Color(0xFFFF00FF)  // Magenta
        else -> Color.White
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row {
            Text(
                text = entry.timestamp,
                color = Color(0xFF666666),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.width(100.dp)
            )

            Text(
                text = entry.level,
                color = textColor,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.width(20.dp)
            )

            Text(
                text = entry.tag,
                color = textColor,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.width(80.dp)
            )

            Text(
                text = entry.message,
                color = textColor,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f)
            )
        }

        // Mostra la stacktrace se presente
        if (entry.throwable != null) {
            Text(
                text = entry.throwable.stackTraceToString(),
                color = Color(0xFFFF6699),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(start = 150.dp)
            )
        }
    }
}

