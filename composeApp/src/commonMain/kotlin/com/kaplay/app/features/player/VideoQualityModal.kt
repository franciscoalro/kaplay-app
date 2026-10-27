package com.kaplay.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kaplay.app.core.ui.kaplay
import kaplay.composeapp.generated.resources.Res
import kaplay.composeapp.generated.resources.compose_player_quality_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun VideoQualityModal(
    visible: Boolean,
    qualities: List<VideoQuality>,
    onQualitySelected: (VideoQuality) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerOverlayScaffold(visible = visible, onDismiss = onDismiss, modifier = modifier,
        contentPadding = PaddingValues(start = 44.dp, end = 44.dp, top = 28.dp, bottom = 64.dp)) {
        Column(Modifier.width(444.dp).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
            Text(stringResource(Res.string.compose_player_quality_title), color = Color.White, style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 8.dp))
            LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 8.dp)) {
                items(qualities, key = { it.height ?: 0 }) { quality ->
                    val tokens = MaterialTheme.kaplay
                    var focused by remember(quality.height) { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .onFocusChanged { focused = it.isFocused }
                        .focusable()
                        .border(
                            width = 2.dp,
                            color = if (focused) tokens.colors.accent else Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .background(if (quality.isSelected) tokens.colors.accent else Color.Transparent)
                        .clickable { onQualitySelected(quality) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(quality.label, color = if (quality.isSelected) tokens.colors.onAccent else Color.White,
                            style = MaterialTheme.typography.titleMedium)
                        if (quality.isSelected) Icon(Icons.Rounded.Check, null, tint = tokens.colors.onAccent)
                    }
                }
            }
        }
    }
}
