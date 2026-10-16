package com.antoninofaro.welcometool.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun VerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    thumbWidth: Dp = 6.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    thumbColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
) {
    val scope = rememberCoroutineScope()

    val totalItemCount by remember {
        derivedStateOf { listState.layoutInfo.totalItemsCount }
    }

    if (totalItemCount <= 0) return

    val firstVisibleIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }

    val visibleItemCount by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.size }
    }

    val progress by remember {
        derivedStateOf {
            if (totalItemCount <= visibleItemCount || totalItemCount <= 1) 0f
            else firstVisibleIndex.toFloat() / (totalItemCount - visibleItemCount).coerceAtLeast(1)
        }
    }

    Box(
        modifier = modifier
            .width(20.dp)
            .fillMaxHeight()
            .pointerInput(totalItemCount) {
                detectTapGestures { offset ->
                    val fraction = (offset.y / size.height).coerceIn(0f, 1f)
                    val targetIndex = (fraction * (totalItemCount - 1)).toInt()
                    scope.launch {
                        listState.scrollToItem(targetIndex)
                    }
                }
            }
            .pointerInput(totalItemCount) {
                detectDragGestures { change, _ ->
                    val fraction = (change.position.y / size.height).coerceIn(0f, 1f)
                    val targetIndex = (fraction * (totalItemCount - 1)).toInt()
                    scope.launch {
                        listState.scrollToItem(targetIndex)
                    }
                }
            }
    ) {
        // Track background
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(thumbWidth)
                .fillMaxHeight()
                .padding(vertical = 4.dp)
                .background(trackColor, RoundedCornerShape(thumbWidth / 2))
        )

        // Thumb
        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(thumbWidth)
                .fillMaxHeight()
                .padding(vertical = 4.dp)
        ) {
            val trackHeight = maxHeight
            val thumbHeight = (trackHeight * (visibleItemCount.toFloat() / totalItemCount.toFloat().coerceAtLeast(1f))).coerceAtLeast(24.dp)
            val availableSpace = trackHeight - thumbHeight
            val scrollOffset = if (availableSpace > 0.dp) {
                availableSpace * progress
            } else 0.dp

            Box(
                modifier = Modifier
                    .size(width = thumbWidth, height = thumbHeight)
                    .offset(y = scrollOffset)
                    .background(thumbColor, RoundedCornerShape(thumbWidth / 2))
            )
        }
    }
}
