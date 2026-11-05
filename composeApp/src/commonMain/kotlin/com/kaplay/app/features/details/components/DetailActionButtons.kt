package com.kaplay.app.features.details.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kaplay.app.core.ui.AppIconResource
import com.kaplay.app.core.ui.KAColors
import com.kaplay.app.core.ui.KAShapes
import com.kaplay.app.core.ui.appIconPainter
import androidx.compose.ui.graphics.Color
import kaplay.composeapp.generated.resources.Res
import kaplay.composeapp.generated.resources.action_play
import kaplay.composeapp.generated.resources.details_actions_menu_label
import kaplay.composeapp.generated.resources.hero_add_to_library
import kaplay.composeapp.generated.resources.hero_mark_unwatched
import kaplay.composeapp.generated.resources.hero_mark_watched
import kaplay.composeapp.generated.resources.hero_remove_from_library
import kaplay.composeapp.generated.resources.playback_unavailable
import kaplay.composeapp.generated.resources.random_episode_title
import kaplay.composeapp.generated.resources.shuffle_stop
import org.jetbrains.compose.resources.stringResource

import androidx.compose.ui.unit.sp

internal val DetailButtonRadius = 12.dp
internal val DetailButtonShape = RoundedCornerShape(DetailButtonRadius)
internal val DetailActionGap = 8.dp

data class DetailSecondaryAction(
    val label: String,
    val icon: ImageVector,
    val isActive: Boolean = false,
    val onClick: () -> Unit = {},
    val onLongClick: (() -> Unit)? = null,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailActionButtons(
    modifier: Modifier = Modifier,
    playLabel: String = stringResource(Res.string.action_play),
    playEnabled: Boolean = true,
    pinnedAction: DetailSecondaryAction? = null,
    secondaryActions: List<DetailSecondaryAction> = emptyList(),
    actionsMenuLabel: String = stringResource(Res.string.details_actions_menu_label),
    isTablet: Boolean = false,
    onPlayClick: () -> Unit = {},
    onPlayLongClick: (() -> Unit)? = null,
) {
    val playPainter = appIconPainter(AppIconResource.PlayerPlay)
    val buttonHeight = if (isTablet) 52.dp else 48.dp
    val iconButtonSize = buttonHeight
    val playShape = DetailButtonShape
    val hapticFeedback = LocalHapticFeedback.current
    var actionsExpanded by remember { mutableStateOf(false) }
    val menuProgress by animateFloatAsState(
        targetValue = if (actionsExpanded) 1f else 0f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "detail_action_menu_progress",
    )
    val hasSecondaryActions = secondaryActions.isNotEmpty()

    Box(
        modifier = modifier
            .widthIn(max = if (isTablet) 520.dp else 420.dp)
            .fillMaxWidth()
            .height(buttonHeight),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var isPlayFocused by remember { mutableStateOf(false) }
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(buttonHeight)
                    .then(
                        if (isPlayFocused) Modifier.border(2.dp, Color.White, playShape) else Modifier
                    ),
                shape = playShape,
                color = if (playEnabled) KAColors.Primary else KAColors.SurfaceVariant,
                contentColor = if (playEnabled) Color.White else Color.White.copy(alpha = 0.45f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isPlayFocused = it.isFocused }
                        .combinedClickable(
                            enabled = playEnabled,
                            onClick = {
                                onPlayClick()
                            },
                            onLongClick = onPlayLongClick,
                            role = Role.Button,
                        )
                        .height(buttonHeight),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = playPainter,
                        contentDescription = null,
                        modifier = Modifier.size(if (isTablet) 20.dp else 18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = playLabel,
                        style = if (isTablet) {
                            MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        } else {
                            MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (pinnedAction != null) {
                Spacer(modifier = Modifier.width(DetailActionGap))
                DetailIconAction(
                    label = pinnedAction.label,
                    icon = pinnedAction.icon,
                    active = pinnedAction.isActive,
                    progress = 1f,
                    size = iconButtonSize,
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        pinnedAction.onClick()
                    },
                )
            }

            if (hasSecondaryActions) {
                Spacer(modifier = Modifier.width(DetailActionGap))
                secondaryActions.forEachIndexed { index, action ->
                    Box(
                        modifier = Modifier
                            .width(iconButtonSize * menuProgress)
                            .height(iconButtonSize)
                            .graphicsLayer {
                                clip = true
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (actionsExpanded || menuProgress > 0.01f) {
                            DetailIconAction(
                                label = action.label,
                                icon = action.icon,
                                active = action.isActive,
                                progress = menuProgress,
                                size = iconButtonSize,
                                onClick = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    action.onClick()
                                },
                                onLongClick = action.onLongClick?.let { longClick ->
                                    {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        longClick()
                                    }
                                },
                            )
                        }
                    }

                    if (index != secondaryActions.lastIndex) {
                        Spacer(modifier = Modifier.width(DetailActionGap * menuProgress))
                    }
                }
                Spacer(modifier = Modifier.width(DetailActionGap * menuProgress))
            }

            if (hasSecondaryActions) {
                var isMenuFocused by remember { mutableStateOf(false) }
                Surface(
                    modifier = Modifier
                        .size(iconButtonSize)
                        .then(
                            if (isMenuFocused) Modifier.border(2.dp, Color.White, DetailButtonShape) else Modifier
                        ),
                    shape = DetailButtonShape,
                    color = if (actionsExpanded) {
                        KAColors.Primary
                    } else {
                        KAColors.SurfaceVariant
                    },
                    contentColor = Color.White,
                ) {
                    Box(
                        modifier = Modifier
                            .size(iconButtonSize)
                            .onFocusChanged { isMenuFocused = it.isFocused }
                            .clickable(role = Role.Button) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                actionsExpanded = !actionsExpanded
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = actionsMenuLabel,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer {
                                    rotationZ = 90f * menuProgress
                                },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailActions(
    playLabel: String,
    playEnabled: Boolean,
    isSaved: Boolean,
    isWatched: Boolean,
    isTablet: Boolean,
    shuffleEnabled: Boolean,
    onPlayClick: () -> Unit,
    onPlayLongClick: (() -> Unit)?,
    onShuffleClick: (() -> Unit)?,
    onWatchedClick: () -> Unit,
    onSaveClick: () -> Unit,
    onSaveLongClick: (() -> Unit)?,
) {
    val shuffleAction = onShuffleClick?.let { onClick ->
        DetailSecondaryAction(
            label = stringResource(if (shuffleEnabled) Res.string.shuffle_stop else Res.string.random_episode_title),
            icon = Icons.Default.Shuffle,
            isActive = shuffleEnabled,
            onClick = onClick,
        )
    }
    DetailActionButtons(
        playLabel = if (playEnabled) playLabel else stringResource(Res.string.playback_unavailable),
        playEnabled = playEnabled,
        pinnedAction = shuffleAction?.takeIf { shuffleEnabled },
        secondaryActions = buildList {
            if (!shuffleEnabled) shuffleAction?.let(::add)
            add(
                DetailSecondaryAction(
                    label = stringResource(if (isWatched) Res.string.hero_mark_unwatched else Res.string.hero_mark_watched),
                    icon = if (isWatched) Icons.Default.CheckCircle else Icons.Default.CheckCircleOutline,
                    isActive = isWatched,
                    onClick = onWatchedClick,
                ),
            )
            add(
                DetailSecondaryAction(
                    label = stringResource(if (isSaved) Res.string.hero_remove_from_library else Res.string.hero_add_to_library),
                    icon = if (isSaved) Icons.Default.Check else Icons.Default.Add,
                    isActive = isSaved,
                    onClick = onSaveClick,
                    onLongClick = onSaveLongClick,
                ),
            )
        },
        isTablet = isTablet,
        onPlayClick = onPlayClick,
        onPlayLongClick = onPlayLongClick,
    )
}

@Composable
private fun DetailIconAction(
    label: String,
    icon: ImageVector,
    active: Boolean,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp,
    onLongClick: (() -> Unit)? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .graphicsLayer {
                alpha = progress
                scaleX = 0.86f + (0.14f * progress)
                scaleY = 0.86f + (0.14f * progress)
            }
            .then(
                if (isFocused) Modifier.border(2.dp, Color.White, KAShapes.Button) else Modifier
            ),
        shape = DetailButtonShape,
        color = if (active) {
            KAColors.Primary
        } else {
            KAColors.SurfaceVariant
        },
        contentColor = Color.White,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .onFocusChanged { isFocused = it.isFocused }
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    role = Role.Button,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(21.dp),
            )
        }
    }
}
