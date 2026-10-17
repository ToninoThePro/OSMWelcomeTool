package com.antoninofaro.welcometool.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.antoninofaro.welcometool.data.entity.ChangesetEntity
import com.antoninofaro.welcometool.data.model.ChangedElement
import com.antoninofaro.welcometool.data.model.ChangedElementGroup
import com.antoninofaro.welcometool.ui.components.BBoxMiniMap
import com.antoninofaro.welcometool.data.model.ChangesetUiModel
import com.antoninofaro.welcometool.ui.components.fadingEdge
import com.antoninofaro.welcometool.ui.theme.WelcomeToolTheme
import com.antoninofaro.welcometool.utils.formatRelativeTime
import java.text.SimpleDateFormat
import java.util.Locale

enum class DiffFilter {
    ADDED, MODIFIED, DELETED
}

fun formatElementName(el: ChangedElement): String {
    val name = el.tags["name"]
    val primaryVal = el.tags.entries.firstOrNull { it.key != "created_by" && it.key != "source" && it.key != "name" }?.value
    val prefix = name ?: primaryVal?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } ?: el.type.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    return "$prefix ${el.type.lowercase()}:${el.id}"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChangesetDetailScreen(
    uiModel: ChangesetUiModel,
    elements: ChangedElementGroup?,
    isElementsLoading: Boolean,
    onNavigateUp: () -> Unit,
    onUserClick: (Long) -> Unit,
    onExpandElements: () -> Unit
) {
    val entity = uiModel.entity
    var activeFilter by remember { mutableStateOf<DiffFilter?>(null) }
    val uriHandler = LocalUriHandler.current
    
    val timestamp = com.antoninofaro.welcometool.utils.parseIsoToMillis(entity.createdAt)

    val createCount = maxOf(entity.createCount, elements?.creates?.size ?: 0)
    val modifyCount = maxOf(entity.modifyCount, elements?.modifies?.size ?: 0)
    val deleteCount = maxOf(entity.deleteCount, elements?.deletes?.size ?: 0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("#${entity.id} Changeset") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .fadingEdge()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            
            // 1. Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val comment = entity.tagsJson?.let {
                        val match = "\"comment\":\"(.*?)\"".toRegex().find(it)
                        match?.groups?.get(1)?.value
                    } ?: "No comment"
                    
                    Text(
                        text = comment,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "#${entity.id} • ${formatRelativeTime(timestamp, "now", "%d min ago", "%d hours ago")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onUserClick(entity.uid) }
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "By ${entity.user}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { uriHandler.openUri("https://www.openstreetmap.org/changeset/${entity.id}") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "VEDI SU OSM",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Map Area Card (First element under title card)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Map Area", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    BBoxMiniMap(
                        minLon = entity.minLon,
                        minLat = entity.minLat,
                        maxLon = entity.maxLon,
                        maxLat = entity.maxLat
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("BBox: ${entity.minLon}, ${entity.minLat}, ${entity.maxLon}, ${entity.maxLat}", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. OSMCha Analysis Card (fills all available space)
            if (entity.isOsmchaChecked) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("OSMCha Analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (entity.osmchaIsSuspect || entity.osmchaHarmful == true) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Flagged as Suspect/Harmful", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            
                            uiModel.reasons.forEach { reason ->
                                AssistChip(
                                    onClick = { },
                                    label = { Text(reason) },
                                    leadingIcon = { Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 4. Merged Diff Summary & Changed Elements
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Diff Summary & Elements", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        DiffStatButton(
                            label = "Added",
                            count = createCount,
                            color = Color(0xFF436900),
                            contentColor = Color.White,
                            isSelected = activeFilter == DiffFilter.ADDED,
                            onClick = {
                                if (activeFilter == DiffFilter.ADDED) {
                                    activeFilter = null
                                } else {
                                    activeFilter = DiffFilter.ADDED
                                    if (elements == null) onExpandElements()
                                }
                            }
                        )
                        DiffStatButton(
                            label = "Modified",
                            count = modifyCount,
                            color = Color(0xFF8A6500),
                            contentColor = Color.White,
                            isSelected = activeFilter == DiffFilter.MODIFIED,
                            onClick = {
                                if (activeFilter == DiffFilter.MODIFIED) {
                                    activeFilter = null
                                } else {
                                    activeFilter = DiffFilter.MODIFIED
                                    if (elements == null) onExpandElements()
                                }
                            }
                        )
                        DiffStatButton(
                            label = "Deleted",
                            count = deleteCount,
                            color = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            isSelected = activeFilter == DiffFilter.DELETED,
                            onClick = {
                                if (activeFilter == DiffFilter.DELETED) {
                                    activeFilter = null
                                } else {
                                    activeFilter = DiffFilter.DELETED
                                    if (elements == null) onExpandElements()
                                }
                            }
                        )
                    }

                    AnimatedVisibility(
                        visible = activeFilter != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            if (isElementsLoading) {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            } else if (elements != null) {
                                when (activeFilter) {
                                    DiffFilter.ADDED -> ElementGroupSection(
                                        title = "Added Elements ($createCount)",
                                        elements = elements.creates,
                                        color = Color(0xFF436900),
                                        onElementClick = { el ->
                                            uriHandler.openUri("https://www.openstreetmap.org/${el.type.lowercase()}/${el.id}")
                                        }
                                    )
                                    DiffFilter.MODIFIED -> ElementGroupSection(
                                        title = "Modified Elements ($modifyCount)",
                                        elements = elements.modifies,
                                        color = Color(0xFF8A6500),
                                        onElementClick = { el ->
                                            uriHandler.openUri("https://www.openstreetmap.org/${el.type.lowercase()}/${el.id}")
                                        }
                                    )
                                    DiffFilter.DELETED -> ElementGroupSection(
                                        title = "Deleted Elements ($deleteCount)",
                                        elements = elements.deletes,
                                        color = MaterialTheme.colorScheme.error,
                                        onElementClick = { el ->
                                            uriHandler.openUri("https://www.openstreetmap.org/${el.type.lowercase()}/${el.id}")
                                        }
                                    )
                                    null -> {}
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun DiffStatButton(
    label: String,
    count: Int,
    color: Color,
    contentColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isSelected) color else color.copy(alpha = 0.15f),
            contentColor = if (isSelected) contentColor else color,
            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, color) else null,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) color else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ElementGroupSection(
    title: String,
    elements: List<ChangedElement>,
    color: Color,
    onElementClick: (ChangedElement) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (elements.isEmpty()) {
            Text(
                text = "No elements recorded",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                elements.take(50).forEach { el ->
                    val displayName = formatElementName(el)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onElementClick(el) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (el.tags.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = el.tags.entries.joinToString(", ") { "${it.key}=${it.value}" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "View on OSM",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                if (elements.size > 50) {
                    Text(
                        text = "...and ${elements.size - 50} more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChangesetDetailScreenPreview() {
    WelcomeToolTheme {
        val sampleEntity = ChangesetEntity(
            id = 12345678L,
            bbox = "10.0,45.0,11.0,46.0",
            createdAt = "2023-10-25T12:34:56Z",
            closedAt = "2023-10-25T12:40:00Z",
            open = false,
            user = "MappingNewbie99",
            uid = 987654L,
            minLat = 45.1,
            minLon = 10.1,
            maxLat = 45.2,
            maxLon = 10.2,
            numChanges = 15,
            commentsCount = 1,
            tagsJson = "{\"comment\":\"Added new residential road and buildings\",\"created_by\":\"JOSM/1.6.1\"}",
            osmchaScore = 0,
            osmchaIsSuspect = false,
            osmchaHarmful = false,
            createCount = 10,
            modifyCount = 3,
            deleteCount = 2,
            isOsmchaChecked = true
        )
        val sampleUiModel = ChangesetUiModel(
            entity = sampleEntity,
            reasons = listOf("New mapper", "First changeset"),
            isOnline = true
        )
        val sampleElements = ChangedElementGroup(
            creates = listOf(
                ChangedElement("way", 101L, "create", mapOf("highway" +
                        "" to "residential", "name" to "Via Roma")),
                ChangedElement("node", 102L, "create", mapOf("amenity" to "bench"))
            ),
            modifies = listOf(
                ChangedElement("node", 103L, "modify", mapOf("natural" to "tree"))
            ),
            deletes = emptyList()
        )
        ChangesetDetailScreen(
            uiModel = sampleUiModel,
            elements = sampleElements,
            isElementsLoading = false,
            onNavigateUp = {},
            onUserClick = {},
            onExpandElements = {}
        )
    }
}
