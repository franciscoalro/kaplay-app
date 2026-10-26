package com.kaplay.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

@Composable
fun KAPlayProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = KAPlayTokens.Space.s4,
    trackColor: Color = MaterialTheme.kaplay.colors.playerTimelineTrack,
    fillColor: Color = MaterialTheme.kaplay.colors.playerTimelineFill,
) {
    val tokens = MaterialTheme.kaplay
    val clampedProgress = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(tokens.shapes.chip)
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clampedProgress)
                .width(KAPlayTokens.Space.none)
                .height(height)
                .clip(tokens.shapes.chip)
                .background(fillColor),
        )
    }
}
