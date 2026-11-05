package com.kaplay.app.features.details.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAddCheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kaplay.app.core.i18n.localizedSeasonEpisodeCode
import com.kaplay.app.core.ui.KAColors
import com.kaplay.app.core.ui.KAPlayBottomSheetActionRow
import com.kaplay.app.core.ui.KAPlayBottomSheetDivider
import com.kaplay.app.core.ui.KAPlayModalBottomSheet
import com.kaplay.app.core.ui.KAShapes
import com.kaplay.app.core.ui.dismissKAPlayBottomSheet
import com.kaplay.app.core.ui.kaplaySafeBottomPadding
import com.kaplay.app.features.details.MetaVideo
import com.kaplay.app.features.details.formatRuntimeFromMinutes
import com.kaplay.app.features.watchprogress.WatchProgressEntry
import kaplay.composeapp.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeWatchedActionSheet(
    episode: MetaVideo,
    seasonLabel: String,
    isEpisodeWatched: Boolean,
    canMarkPreviousEpisodes: Boolean,
    arePreviousEpisodesWatched: Boolean,
    isSeasonWatched: Boolean,
    progressEntry: WatchProgressEntry? = null,
    onDismiss: () -> Unit,
    onPlayFromBeginning: (() -> Unit)? = null,
    onResumeEpisode: (() -> Unit)? = null,
    onToggleWatched: () -> Unit,
    onTogglePreviousWatched: () -> Unit,
    onToggleSeasonWatched: () -> Unit,
    showPlayManually: Boolean = false,
    onPlayManually: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    var showOverviewDetails by remember { mutableStateOf(false) }

    KAPlayModalBottomSheet(
        onDismissRequest = {
            coroutineScope.launch {
                dismissKAPlayBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
            }
        },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = kaplaySafeBottomPadding(16.dp)),
        ) {
            EpisodeActionSheetHeader(
                episode = episode,
                seasonLabel = seasonLabel,
            )

            AnimatedVisibility(visible = showOverviewDetails && !episode.overview.isNullOrBlank()) {
                Text(
                    text = episode.overview.orEmpty(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    ),
                    color = KAColors.TextSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            KAPlayBottomSheetDivider()

            // 1. Play from beginning
            if (onPlayFromBeginning != null) {
                KAPlayBottomSheetActionRow(
                    icon = Icons.Rounded.PlayArrow,
                    title = stringResource(Res.string.action_play),
                    subtitle = "Começar do início",
                    onClick = {
                        onPlayFromBeginning()
                        coroutineScope.launch {
                            dismissKAPlayBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
                        }
                    },
                )
            }

            // 2. Resume playback (only if progress exists and is not completed)
            val hasValidResume = progressEntry != null && progressEntry.lastPositionMs > 15_000L && !progressEntry.isCompleted
            if (hasValidResume && onResumeEpisode != null) {
                val progressMinutes = (progressEntry.lastPositionMs / 60_000L).coerceAtLeast(1L)
                KAPlayBottomSheetActionRow(
                    icon = Icons.Rounded.PlayCircle,
                    title = "Continuar de onde parei",
                    subtitle = "Retomar aos $progressMinutes min",
                    onClick = {
                        onResumeEpisode()
                        coroutineScope.launch {
                            dismissKAPlayBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
                        }
                    },
                )
            }

            // 3. Toggle watched state
            KAPlayBottomSheetActionRow(
                icon = Icons.Default.CheckCircle,
                title = if (isEpisodeWatched) {
                    stringResource(Res.string.episode_mark_unwatched)
                } else {
                    stringResource(Res.string.episode_mark_watched)
                },
                subtitle = "Registrar no histórico do perfil",
                onClick = {
                    onToggleWatched()
                    coroutineScope.launch {
                        dismissKAPlayBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
                    }
                },
            )

            // 4. Mark previous episodes
            if (canMarkPreviousEpisodes) {
                KAPlayBottomSheetActionRow(
                    icon = Icons.Default.DoneAll,
                    title = if (arePreviousEpisodesWatched) {
                        stringResource(Res.string.episode_mark_previous_unwatched)
                    } else {
                        stringResource(Res.string.episode_mark_previous_watched)
                    },
                    onClick = {
                        onTogglePreviousWatched()
                        coroutineScope.launch {
                            dismissKAPlayBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
                        }
                    },
                )
            }

            // 5. Mark season
            KAPlayBottomSheetActionRow(
                icon = Icons.Default.PlaylistAddCheckCircle,
                title = if (isSeasonWatched) {
                    stringResource(Res.string.episode_mark_season_unwatched, seasonLabel)
                } else {
                    stringResource(Res.string.episode_mark_season_watched, seasonLabel)
                },
                onClick = {
                    onToggleSeasonWatched()
                    coroutineScope.launch {
                        dismissKAPlayBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
                    }
                },
            )

            // 6. View episode details (toggle synopsis)
            if (!episode.overview.isNullOrBlank()) {
                KAPlayBottomSheetActionRow(
                    icon = Icons.Rounded.Info,
                    title = "Ver detalhes do episódio",
                    subtitle = "Sinopse e informações completas",
                    selected = showOverviewDetails,
                    onClick = {
                        showOverviewDetails = !showOverviewDetails
                    },
                )
            }

            // 7. Play manually (when supported)
            if (showPlayManually && onPlayManually != null) {
                KAPlayBottomSheetActionRow(
                    icon = Icons.Default.PlayArrow,
                    title = stringResource(Res.string.play_manually),
                    onClick = {
                        onPlayManually()
                        coroutineScope.launch {
                            dismissKAPlayBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonWatchedActionSheet(
    seasonLabel: String,
    isSeasonWatched: Boolean,
    canMarkPreviousSeasons: Boolean,
    onDismiss: () -> Unit,
    onToggleSeasonWatched: () -> Unit,
    onMarkPreviousSeasonsWatched: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    KAPlayModalBottomSheet(
        onDismissRequest = {
            coroutineScope.launch {
                dismissKAPlayBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
            }
        },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = kaplaySafeBottomPadding(16.dp)),
        ) {
            Text(
                text = seasonLabel,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )
            KAPlayBottomSheetDivider()
            KAPlayBottomSheetActionRow(
                icon = Icons.Default.PlaylistAddCheckCircle,
                title = if (isSeasonWatched) {
                    stringResource(Res.string.episode_mark_season_unwatched, seasonLabel)
                } else {
                    stringResource(Res.string.episode_mark_season_watched, seasonLabel)
                },
                onClick = {
                    onToggleSeasonWatched()
                    coroutineScope.launch {
                        dismissKAPlayBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
                    }
                },
            )
            if (canMarkPreviousSeasons) {
                KAPlayBottomSheetActionRow(
                    icon = Icons.Default.DoneAll,
                    title = stringResource(Res.string.episode_mark_previous_seasons_watched),
                    onClick = {
                        onMarkPreviousSeasonsWatched()
                        coroutineScope.launch {
                            dismissKAPlayBottomSheet(sheetState = sheetState, onDismiss = onDismiss)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun EpisodeActionSheetHeader(
    episode: MetaVideo,
    seasonLabel: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (episode.thumbnail != null) {
            AsyncImage(
                model = episode.thumbnail,
                contentDescription = episode.title,
                modifier = Modifier
                    .width(100.dp)
                    .aspectRatio(16f / 9f)
                    .clip(KAShapes.Poster)
                    .background(KAColors.SurfaceVariant),
                contentScale = ContentScale.Crop,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    localizedSeasonEpisodeCode(
                        seasonNumber = episode.season,
                        episodeNumber = episode.episode,
                    )?.let {
                        append(it)
                        append(" • ")
                    }
                    append(seasonLabel)
                },
                style = MaterialTheme.typography.bodySmall,
                color = KAColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val runtimeLabel = episode.runtime?.takeIf { it > 0 }?.let { formatRuntimeFromMinutes(it) }
            if (runtimeLabel != null) {
                Text(
                    text = runtimeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = KAColors.TextMuted,
                    maxLines = 1,
                )
            }
        }
    }
}
