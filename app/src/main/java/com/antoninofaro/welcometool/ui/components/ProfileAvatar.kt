package com.antoninofaro.welcometool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import coil3.compose.AsyncImage
import com.antoninofaro.welcometool.utils.AvatarUtils

/**
 * Composable for displaying user profile avatars.
 * Shows actual image if available, tries Gravatar with identicon fallback,
 * and finally falls back to initials.
 */
@Composable
fun ProfileAvatar(
    displayName: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    contentDescription: String = "",
    size: Int = 128
) {
    // Get the image URL, using Gravatar as fallback
    val finalImageUrl = remember(displayName, imageUrl, size) {
        AvatarUtils.getProfileImageUrl(imageUrl, displayName, size)
    }

    if (!finalImageUrl.isNullOrBlank()) {
        AsyncImage(
            model = finalImageUrl,
            contentDescription = contentDescription,
            modifier = modifier.clip(MaterialTheme.shapes.medium),
            contentScale = ContentScale.Crop
        )
    } else {

        // Fallback to initials
        Box(
            modifier = modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clip(MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center
        ) {
            if (displayName.isNotBlank()) {
                Text(
                    text = displayName.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
