package com.antoninofaro.welcometool.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbsUpDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antoninofaro.welcometool.R
import com.antoninofaro.welcometool.data.model.OsmUser
import com.antoninofaro.welcometool.domain.UserAnalysis
import com.antoninofaro.welcometool.ui.components.ProfileAvatar
import com.antoninofaro.welcometool.utils.formatRelativeTime
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDetailScreen(
    user: OsmUser,
    analysis: UserAnalysis,
    osmchaState: OsmchaState,
    osmchaLikes: Int,
    osmchaDislikes: Int,
    osmchaLastChecked: Long,
    lastUpdated: Long = 0L,
    isOnline: Boolean = true,
    onOsmchaRefresh: () -> Unit,
    onNavigateUp: () -> Unit,
    onToggleWelcome: () -> Unit
) {
    val scrollState = rememberScrollState()
    val uriHandler = LocalUriHandler.current
    val datePattern = stringResource(R.string.date_format_pattern)
    var isBioExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = stringResource(R.string.user_detail_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_desc))
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
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header - Avatar with profile image or placeholder
            ProfileAvatar(
                displayName = user.displayName,
                imageUrl = user.img?.href,
                modifier = Modifier.size(120.dp),
                contentDescription = stringResource(R.string.profile_photo_desc, user.displayName),
                size = 120
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = user.displayName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            AssistChip(
                onClick = {},
                label = { Text(stringResource(R.string.user_id_chip, user.id)) },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp)) },
                shape = RoundedCornerShape(12.dp),
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    leadingIconContentColor = MaterialTheme.colorScheme.primary
                )
            )

            if (!user.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(20.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (!isBioExpanded) Modifier.height(50.dp) else Modifier
                                )
                                .animateContentSize()
                        ) {
                            Markdown(
                                content = user.description,
                                modifier = Modifier.fillMaxWidth(),
                                colors = markdownColor(
                                    text = MaterialTheme.colorScheme.onSurface
                                ),
                                typography = markdownTypography(
                                    h1 = MaterialTheme.typography.headlineLarge,
                                    h2 = MaterialTheme.typography.headlineMedium,
                                    h3 = MaterialTheme.typography.titleLarge,
                                    h4 = MaterialTheme.typography.titleMedium,
                                    h5 = MaterialTheme.typography.titleSmall,
                                    h6 = MaterialTheme.typography.labelLarge
                                )
                            )
                        }
                        
                        TextButton(
                            onClick = { isBioExpanded = !isBioExpanded },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(
                                if (isBioExpanded) stringResource(R.string.show_less) else stringResource(R.string.show_more),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

    // Offline banner
    if (!isOnline) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.offline_banner),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Action Button with dropdown for sending message
            var expanded by remember { mutableStateOf(false) }
            val btnColor = if (analysis.isWelcomed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Button(
                    onClick = onToggleWelcome,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(
                        topStart = 16.dp, bottomStart = 16.dp,
                        topEnd = if (analysis.isWelcomed) 16.dp else 0.dp,
                        bottomEnd = if (analysis.isWelcomed) 16.dp else 0.dp
                    ),
                    colors = ButtonDefaults.buttonColors(containerColor = btnColor)
                ) {
                    Icon(
                        imageVector = if (analysis.isWelcomed) Icons.Default.Check else Icons.AutoMirrored.Filled.Send,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (analysis.isWelcomed) stringResource(R.string.already_welcomed_btn) else stringResource(R.string.mark_welcomed_btn),
                        fontWeight = FontWeight.Bold
                    )
                }
                if (!analysis.isWelcomed) {
                    Box {
                        Button(
                            onClick = { expanded = true },
                            modifier = Modifier
                                .height(56.dp)
                                .width(48.dp),
                            shape = RoundedCornerShape(
                                topStart = 0.dp, bottomStart = 0.dp,
                                topEnd = 16.dp, bottomEnd = 16.dp
                            ),
                            colors = ButtonDefaults.buttonColors(containerColor = btnColor),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.options_desc))
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.send_and_welcome)) },
                                onClick = {
                                    expanded = false
                                    uriHandler.openUri("https://www.openstreetmap.org/message/new/${user.displayName}")
                                    onToggleWelcome()
                                },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, null) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Stats Cards
            Text(
                text = stringResource(R.string.profile_analysis_section).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.2.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatusCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.status_newcomer),
                    isActive = analysis.isNewcomer,
                    icon = Icons.Default.Star,
                    color = MaterialTheme.colorScheme.primary,
                )
                StatusCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.status_returning),
                    isActive = analysis.isReturning,
                    icon = Icons.Default.Refresh,
                    color = MaterialTheme.colorScheme.secondary,
                )
                StatusCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.status_power_user),
                    isActive = analysis.isPowerUser,
                    icon = Icons.Default.Star,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Details List
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.registration_date), fontWeight = FontWeight.Medium) },
                        supportingContent = { Text(user.accountCreated.take(10)) },
                        leadingContent = {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = stringResource(R.string.registration_date),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.total_edits), fontWeight = FontWeight.Medium) },
                        supportingContent = { Text(analysis.totalEdits.toString()) },
                        leadingContent = { 
                            Icon(
                                Icons.Default.Edit, 
                                contentDescription = stringResource(R.string.edits_icon_desc),
                                tint = MaterialTheme.colorScheme.primary
                            ) 
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    
                    when (osmchaState) {
                        OsmchaState.NoToken -> ListItem(
                            headlineContent = { Text("OSMCHA", fontWeight = FontWeight.Medium) },
                            supportingContent = { Text(stringResource(R.string.osmcha_no_token)) },
                            leadingContent = { Icon(Icons.Default.ThumbsUpDown, null, tint = MaterialTheme.colorScheme.secondary) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        OsmchaState.Loading -> {
                            val isRefresh = osmchaLikes > 0 || osmchaDislikes > 0
                            ListItem(
                                headlineContent = { Text("OSMCHA", fontWeight = FontWeight.Medium) },
                                supportingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                        if (isRefresh) {
                                            Text(stringResource(R.string.osmcha_updating, osmchaLikes, osmchaDislikes))
                                        } else {
                                            Text(stringResource(R.string.osmcha_searching))
                                        }
                                    }
                                },
                                leadingContent = { Icon(Icons.Default.ThumbsUpDown, null, tint = MaterialTheme.colorScheme.secondary) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                        OsmchaState.Loaded -> ListItem(
                            headlineContent = { Text("OSMCHA", fontWeight = FontWeight.Medium) },
                            supportingContent = { Text(stringResource(R.string.osmcha_rating, osmchaLikes, osmchaDislikes)) },
                            leadingContent = { Icon(Icons.Default.ThumbsUpDown, null, tint = MaterialTheme.colorScheme.secondary) },
                            trailingContent = {
                                IconButton(onClick = onOsmchaRefresh) {
                                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh_desc), tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        OsmchaState.Error -> ListItem(
                            headlineContent = { Text("OSMCHA", fontWeight = FontWeight.Medium) },
                            supportingContent = { Text(stringResource(R.string.osmcha_error)) },
                            leadingContent = { Icon(Icons.Default.ThumbsUpDown, null, tint = MaterialTheme.colorScheme.secondary) },
                            modifier = Modifier.clickable { onOsmchaRefresh() },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                    if (osmchaLastChecked > 0L) {
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.last_osmcha_check), fontWeight = FontWeight.Medium) },
                            supportingContent = { Text(formatRelativeTime(osmchaLastChecked, stringResource(R.string.time_now), stringResource(R.string.time_minutes_ago), stringResource(R.string.time_hours_ago), datePattern)) },
                            leadingContent = {
                                Icon(Icons.Default.ThumbsUpDown, contentDescription = stringResource(R.string.last_osmcha_check), tint = MaterialTheme.colorScheme.secondary)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.last_activity), fontWeight = FontWeight.Medium) },
                        supportingContent = { Text(analysis.lastActiveDate?.take(10) ?: stringResource(R.string.not_available)) },
                        leadingContent = {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.last_activity),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    if (lastUpdated > 0L) {
                        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.last_data_sync), fontWeight = FontWeight.Medium) },
                            supportingContent = { Text(formatRelativeTime(lastUpdated, stringResource(R.string.time_now), stringResource(R.string.time_minutes_ago), stringResource(R.string.time_hours_ago), datePattern)) },
                            leadingContent = {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = stringResource(R.string.last_data_sync),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // External Tools
            Text(
                text = stringResource(R.string.external_tools_section).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.2.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExternalLinkButton(
                    text = stringResource(R.string.hdyc_stats),
                    icon = Icons.Default.Info,
                    onClick = { uriHandler.openUri("https://hdyc.neis-one.org/?${user.displayName}") }
                )

                ExternalLinkButton(
                    text = stringResource(R.string.osmcha_history),
                    icon = Icons.Default.AccountCircle,
                    onClick = { uriHandler.openUri("https://osmcha.org/?filters={\"users\":[\"${user.displayName}\"]}") }
                )

                ExternalLinkButton(
                    text = stringResource(R.string.osm_profile),
                    icon = Icons.Default.Person,
                    onClick = { uriHandler.openUri("https://www.openstreetmap.org/user/${user.displayName}") }
                )
            }
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 2.dp else 0.dp)
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
                tint = if (isActive) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
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
            .height(56.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.ExitToApp,
            contentDescription = stringResource(R.string.external_link_desc),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}
