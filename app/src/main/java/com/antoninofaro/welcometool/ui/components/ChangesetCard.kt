package com.antoninofaro.welcometool.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.antoninofaro.welcometool.data.entity.ChangesetEntity
import com.antoninofaro.welcometool.data.model.ChangesetUiModel
import com.antoninofaro.welcometool.ui.theme.WelcomeToolTheme
import com.antoninofaro.welcometool.utils.formatRelativeTime
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Compact Changeset Card:
 * Line 1: #id • User name                                time
 * Line 2: Comment (left)                                  +X ~Y -Z (right)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChangesetCard(
    uiModel: ChangesetUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val entity = uiModel.entity
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Line 1: #12345678 • User                       time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "#${entity.id}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = entity.user,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                val timestamp = try { sdf.parse(entity.createdAt)?.time ?: 0L } catch(e: Exception) { 0L }
                
                Text(
                    text = formatRelativeTime(
                        timestamp,
                        "now",
                        "%d min ago",
                        "%d h ago"
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Line 2: Comment on left (weighted), Badges on right
            val comment = entity.tagsJson?.let {
                val match = "\"comment\":\"(.*?)\"".toRegex().find(it)
                match?.groups?.get(1)?.value
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (!comment.isNullOrBlank()) comment else "No comment",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (!comment.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val createCount = maxOf(entity.createCount, 0)
                    val modifyCount = maxOf(entity.modifyCount, 0)
                    val deleteCount = maxOf(entity.deleteCount, 0)
                    val hasDiffs = createCount > 0 || modifyCount > 0 || deleteCount > 0

                    if (hasDiffs) {
                        if (createCount > 0) DiffBadge(text = "+$createCount", color = Color(0xFF436900), contentColor = Color.White)
                        if (modifyCount > 0) DiffBadge(text = "~$modifyCount", color = Color(0xFF8A6500), contentColor = Color.White)
                        if (deleteCount > 0) DiffBadge(text = "-$deleteCount", color = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                    } else {
                        val totalChanges = if (entity.numChanges > 0) entity.numChanges else (createCount + modifyCount + deleteCount)
                        val countText = if (totalChanges > 0) "$totalChanges changes" else "0 changes"
                        DiffBadge(text = countText, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    }

                    if (entity.osmchaIsSuspect || entity.osmchaHarmful == true) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(text = "Suspect", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiffBadge(text: String, color: Color, contentColor: Color) {
    Surface(
        color = color,
        contentColor = contentColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Preview(showBackground = true, name = "Compact Card - Badges Right")
@Composable
fun ChangesetCardPreview() {
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
        ChangesetCard(
            uiModel = sampleUiModel,
            onClick = {},
            modifier = Modifier.padding(8.dp)
        )
    }
}
