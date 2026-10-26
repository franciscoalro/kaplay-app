package com.kaplay.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

private val SheetShape = RoundedCornerShape(KAPlayTokens.Space.s28)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KAPlayModalBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.kaplay.colors.surfaceSheet,
    contentColor: Color = MaterialTheme.kaplay.colors.textPrimary,
    showDragHandle: Boolean = true,
    fullHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (usesNativeKAPlayBottomSheet) {
        KAPlayNativeModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            containerColor = containerColor,
            contentColor = contentColor,
            showDragHandle = showDragHandle,
            fullHeight = fullHeight,
            content = content,
        )
    } else {
        val tokens = MaterialTheme.kaplay
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            shape = RectangleShape,
            containerColor = Color.Transparent,
            contentColor = contentColor,
            scrimColor = tokens.colors.overlayScrim,
            dragHandle = null,
        ) {
            CompositionLocalProvider(LocalBottomInsetsConsumed provides true) {
                Column(
                    modifier = modifier
                        .padding(KAPlayTokens.Space.s10)
                        .clip(SheetShape)
                        .background(containerColor)
                        .border(tokens.borders.thin, SurfaceEdge, SheetShape),
                ) {
                    if (showDragHandle) {
                        KAPlayBottomSheetDragHandle(Modifier.align(Alignment.CenterHorizontally))
                    }
                    content()
                }
            }
        }
    }
}

@Composable
fun KAPlayBottomSheetDivider(
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.kaplay
    HorizontalDivider(
        modifier = modifier.padding(horizontal = tokens.spacing.screenHorizontal),
        thickness = tokens.borders.hairline,
        color = tokens.colors.borderDefault,
    )
}

@Composable
fun KAPlayBottomSheetActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    val tokens = MaterialTheme.kaplay
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = KAPlayTokens.Space.s8)
            .heightIn(min = KAPlayTokens.Space.s56)
            .clip(tokens.shapes.compactCard)
            .background(if (selected) tokens.colors.accent.copy(alpha = tokens.opacity.hover) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(KAPlayTokens.Space.s8),
        horizontalArrangement = Arrangement.spacedBy(KAPlayTokens.Space.s14),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(KAPlayTokens.Space.s36)
                    .clip(tokens.shapes.compactCard)
                    .background(tokens.colors.accent.copy(alpha = tokens.opacity.selected)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tokens.colors.accent,
                    modifier = Modifier.size(KAPlayTokens.Icon.md),
                )
            }
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else null,
            color = tokens.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailingContent?.invoke(this)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
suspend fun dismissKAPlayBottomSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
) {
    if (usesNativeKAPlayBottomSheet) {
        dismissNativeKAPlayBottomSheet()
    } else if (sheetState.isVisible) {
        sheetState.hide()
    }
    onDismiss()
}

@Composable
private fun KAPlayBottomSheetDragHandle(modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.kaplay
    Box(
        modifier = modifier
            .padding(top = KAPlayTokens.Space.s10, bottom = KAPlayTokens.Space.s4)
            .size(width = KAPlayTokens.Space.s36, height = KAPlayTokens.Space.s4)
            .clip(tokens.shapes.chip)
            .background(tokens.colors.borderStrong),
    )
}
