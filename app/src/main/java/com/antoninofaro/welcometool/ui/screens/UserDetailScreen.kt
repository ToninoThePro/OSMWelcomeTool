package com.antoninofaro.welcometool.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbsUpDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antoninofaro.welcometool.data.model.OsmUser
import com.antoninofaro.welcometool.domain.UserAnalysis
import com.antoninofaro.welcometool.ui.components.ProfileAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDetailScreen(
    user: OsmUser,
    analysis: UserAnalysis,
    onNavigateUp: () -> Unit,
    onToggleWelcome: () -> Unit
) {
    val scrollState = rememberScrollState()
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Dettagli Mappatore") },
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
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Header - Avatar with profile image or placeholder
            ProfileAvatar(
                displayName = user.displayName,
                imageUrl = user.img?.href,
                modifier = Modifier.size(120.dp),
                contentDescription = "Foto profilo di ${user.displayName}",
                size = 120
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = user.displayName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            AssistChip(
                onClick = {},
                label = { Text("ID: ${user.id}") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action Button
            Button(
                onClick = onToggleWelcome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (analysis.isWelcomed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (analysis.isWelcomed) Icons.Default.Check else {
                        Icons.AutoMirrored.Filled.Send
                    },
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (analysis.isWelcomed) "GIÀ BENVENUTATO" else "SEGNA COME BENVENUTO"
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Stats Cards
            Text(
                text = "ANALISI PROFILO",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusCard(
                    modifier = Modifier.weight(1f),
                    title = "Novizio",
                    isActive = analysis.isNewcomer,
                    icon = Icons.Default.Star,
                    color = MaterialTheme.colorScheme.primary,
                )
                StatusCard(
                    modifier = Modifier.weight(1f),
                    title = "Ritornato",
                    isActive = analysis.isReturning,
                    icon = Icons.Default.Refresh,
                    color = MaterialTheme.colorScheme.secondary,
                )
                StatusCard(
                    modifier = Modifier.weight(1f),
                    title = "Pro",
                    isActive = analysis.isPowerUser,
                    icon = Icons.Default.Star,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Details List
            ListItem(
                headlineContent = { Text("Data Iscrizione") },
                supportingContent = { Text(user.accountCreated.take(10)) },
                leadingContent = {
                    Icon(
                        Icons.Default.DateRange,
                        contentDescription = "Data Iscrizione"
                    )
                }
            )
            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
            ListItem(
                headlineContent = { Text("Totale Modifiche") },
                supportingContent = { Text(analysis.totalEdits.toString()) },
                leadingContent = { Icon(Icons.Default.Edit, contentDescription = "Modifiche") }
            )
            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
            ListItem(
                headlineContent = { Text("OSMCHA rewiew") },
                supportingContent = {
                    Text("👍 ${analysis.osmchaLikes} / 👎 ${analysis.osmchaDislikes}")
                },
                leadingContent = {
                    Icon(
                        Icons.Default.ThumbsUpDown,
                        contentDescription = "OSMCHA review"
                    )
                },
            )
            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
            ListItem(
                headlineContent = { Text("Ultima Attività") },
                supportingContent = { Text(analysis.lastActiveDate?.take(10) ?: "N/D") },
                leadingContent = {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Ultima Attività"
                    )
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // External Tools
            Text(
                text = "STRUMENTI ESTERNI",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            ExternalLinkButton(
                text = "HDYC Stats",
                icon = Icons.Default.Info,
                onClick = { uriHandler.openUri("https://hdyc.neis-one.org/?${user.displayName}") }
            )

            ExternalLinkButton(
                text = "OSMcha History",
                icon = Icons.Default.AccountCircle,
                onClick = { uriHandler.openUri("https://osmcha.org/?filters={\"users\":[\"${user.displayName}\"]}") }
            )

            ExternalLinkButton(
                text = "Profilo OSM",
                icon = Icons.Default.Person,
                onClick = { uriHandler.openUri("https://www.openstreetmap.org/user/${user.displayName}") }
            )
        }
    }
}

@Composable
fun StatusCard(
    modifier: Modifier = Modifier,
    title: String,
    isActive: Boolean,
    icon: ImageVector,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = 0.5f
            )
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) color else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ExternalLinkButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text)
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.ExitToApp,
            contentDescription = "Link esterno",
            modifier = Modifier.size(16.dp)
        )
    }
}