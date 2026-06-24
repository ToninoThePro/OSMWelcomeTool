package com.antoninofaro.welcometool.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Applies a vertical fading edge effect at the top of a scrollable container.
 *
 * @param height The height of the fading edge.
 */
fun Modifier.fadingEdge(height: Dp = 60.dp) = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black, Color.Black),
                startY = 0f,
                endY = height.toPx()
            ),
            blendMode = BlendMode.DstIn
        )
    }
