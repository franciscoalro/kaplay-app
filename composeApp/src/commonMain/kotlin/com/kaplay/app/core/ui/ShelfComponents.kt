package com.kaplay.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kaplay.composeapp.generated.resources.Res
import kaplay.composeapp.generated.resources.home_view_all
import kaplay.composeapp.generated.resources.poster_logo_content_description
import org.jetbrains.compose.resources.stringResource

enum class KAPlayPosterShape {
    Poster,
    Square,
    Landscape,
}

enum class KAPlayViewAllPillSize {
    Default,
    Compact,
}

@Composable
fun <T> KAPlayShelfSection(
    title: String,
    entries: List<T>,
    modifier: Modifier = Modifier,
    headerHorizontalPadding: Dp = 0.dp,
    rowContentPadding: PaddingValues = PaddingValues(0.dp),
    rowModifier: Modifier = Modifier,
    itemSpacing: Dp = 10.dp,
    onViewAllClick: (() -> Unit)? = null,
    viewAllPillSize: KAPlayViewAllPillSize = KAPlayViewAllPillSize.Default,
    key: ((T) -> Any)? = null,
    animatePlacement: Boolean = false,
    state: LazyListState = rememberLazyListState(),
    itemContent: @Composable (T) -> Unit,
) {
    val tokens = MaterialTheme.kaplay
    ScreenActivityEffect(state) { active ->
        if (!active) state.stopScroll()
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap + KAPlayTokens.Space.s2),
    ) {
        if (title.isNotBlank()) {
            KAPlayShelfSectionHeader(
                title = title,
                modifier = Modifier.padding(horizontal = headerHorizontalPadding),
                onViewAllClick = onViewAllClick,
                viewAllPillSize = viewAllPillSize,
            )
        }
        LazyRow(
            modifier = rowModifier.kaplayHorizontalScrollBleed(LocalVerticalBarPadding.current),
            state = state,
            contentPadding = rowContentPadding.withVerticalBar(),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        ) {
            if (key != null) {
                items(
                    items = entries.withDuplicateSafeLazyKeys(key),
                    key = { entry -> entry.lazyKey },
                ) { keyedEntry ->
                    if (animatePlacement) {
                        Box(modifier = Modifier.animateItem()) { itemContent(keyedEntry.value) }
                    } else {
                        itemContent(keyedEntry.value)
                    }
                }
            } else {
                items(entries) { entry ->
                    if (animatePlacement) {
                        Box(modifier = Modifier.animateItem()) { itemContent(entry) }
                    } else {
                        itemContent(entry)
                    }
                }
            }
        }
    }
}

@Composable
fun KAPlayPosterCard(
    title: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    fallbackImageUrl: String? = null,
    shape: KAPlayPosterShape = KAPlayPosterShape.Poster,
    detailLine: String? = null,
    showTitleBelow: Boolean = true,
    bottomLeftLogoUrl: String? = null,
    bottomLeftText: String? = null,
    isWatched: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val posterCardStyle = rememberPosterCardStyleUiState()
    val tokens = MaterialTheme.kaplay
    val cardWidth = shape.cardWidth(basePosterWidthDp = posterCardStyle.widthDp)
    val cardShape = RoundedCornerShape(posterCardStyle.cornerRadiusDp.dp)
    val catalogLogoOverlaySize = catalogLogoOverlaySize(
        basePosterWidthDp = posterCardStyle.widthDp,
        shape = shape,
    )
    val shouldShowTitleBelow = showTitleBelow && !posterCardStyle.hideLabelsEnabled

    Column(
        modifier = modifier.width(cardWidth),
        verticalArrangement = Arrangement.spacedBy(KAPlayTokens.Space.s6),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(shape.aspectRatio)
                .clip(cardShape)
                .background(tokens.colors.surface)
                .kaplayCardDepth(
                    shape = cardShape,
                    surface = KAPlayCardDepthSurface.Posters,
                )
                .posterCardClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    zoomImageUrl = imageUrl,
                    zoomCornerRadius = posterCardStyle.cornerRadiusDp.dp,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl != null) {
                val platformContext = coil3.compose.LocalPlatformContext.current
                val hasFallback = !fallbackImageUrl.isNullOrBlank() && fallbackImageUrl != imageUrl
                val imageModel = remember(imageUrl, fallbackImageUrl, platformContext) {
                    if (hasFallback) {
                        coil3.request.ImageRequest.Builder(platformContext)
                            .data(imageUrl)
                            .memoryCacheKeyExtras(
                                mapOf(com.kaplay.app.core.poster.CustomPosterFallbackInterceptor.FALLBACK_URL_KEY to fallbackImageUrl!!)
                            )
                            .build()
                    } else {
                        imageUrl
                    }
                }
                AsyncImage(
                    model = imageModel,
                    contentDescription = title,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = title,
                    modifier = Modifier.padding(horizontal = KAPlayTokens.Space.s14),
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.colors.textMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (!bottomLeftLogoUrl.isNullOrBlank() || !bottomLeftText.isNullOrBlank()) {
                // Gradient scrim for readability — matching KAPlayTV Modern Home style
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .drawWithCache {
                            val gradient = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.58f to Color.Transparent,
                                    1.0f to Color.Black.copy(alpha = 0.75f)
                                )
                            )
                            onDrawBehind { drawRect(gradient) }
                        }
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = KAPlayTokens.Space.s10, vertical = KAPlayTokens.Space.s10),
                ) {
                    if (!bottomLeftLogoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = bottomLeftLogoUrl,
                            contentDescription = stringResource(Res.string.poster_logo_content_description, title),
                            modifier = Modifier
                                .width(catalogLogoOverlaySize.width)
                                .height(catalogLogoOverlaySize.height),
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.CenterStart,
                        )
                    } else {
                        Text(
                            text = bottomLeftText.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = tokens.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = catalogLogoOverlaySize.textMaxWidth),
                        )
                    }
                }
            }

            KAPlayPosterWatchedOverlay(isWatched = isWatched)
        }
        if (shouldShowTitleBelow) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!detailLine.isNullOrBlank()) {
                Text(
                    text = detailLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Box(modifier = Modifier.height(KAPlayTokens.Space.none))
            }
        } else {
            Box(modifier = Modifier.height(KAPlayTokens.Space.none))
        }
    }
}

@Composable
private fun KAPlayShelfSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onViewAllClick: (() -> Unit)? = null,
    viewAllPillSize: KAPlayViewAllPillSize = KAPlayViewAllPillSize.Default,
) {
    val tokens = MaterialTheme.kaplay
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                color = tokens.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val viewAllPlaceholderModifier = if (onViewAllClick == null) {
                Modifier
                    .alpha(0f)
                    .clearAndSetSemantics { }
            } else {
                Modifier
            }
            KAPlayViewAllPill(
                onClick = onViewAllClick,
                size = viewAllPillSize,
                modifier = viewAllPlaceholderModifier,
            )
        }
    }
}

@Composable
private fun KAPlayViewAllPill(
    onClick: (() -> Unit)?,
    size: KAPlayViewAllPillSize,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.kaplay
    val actionSize = if (size == KAPlayViewAllPillSize.Compact) KAPlayTokens.Space.s32 else KAPlayTokens.Space.s40
    val iconSize = if (size == KAPlayViewAllPillSize.Compact) KAPlayTokens.Icon.sm else tokens.icons.md
    val viewAllText = stringResource(Res.string.home_view_all)

    Box(
        modifier = modifier
            .size(actionSize)
            .background(
                color = tokens.colors.surface,
                shape = RoundedCornerShape(KAPlayTokens.Radius.xl),
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = viewAllText,
            tint = tokens.colors.textMuted,
            modifier = Modifier.size(iconSize),
        )
    }
}

private val KAPlayPosterShape.aspectRatio: Float
    get() = when (this) {
        KAPlayPosterShape.Poster -> 0.675f
        KAPlayPosterShape.Square -> 1f
        KAPlayPosterShape.Landscape -> PosterLandscapeAspectRatio
    }

private data class CatalogLogoOverlaySize(
    val width: Dp,
    val height: Dp,
    val textMaxWidth: Dp,
)

private fun catalogLogoOverlaySize(
    basePosterWidthDp: Int,
    shape: KAPlayPosterShape,
): CatalogLogoOverlaySize =
    if (shape == KAPlayPosterShape.Landscape) {
        when {
            basePosterWidthDp <= 108 -> CatalogLogoOverlaySize(width = 92.dp, height = 24.dp, textMaxWidth = 120.dp)
            basePosterWidthDp <= 120 -> CatalogLogoOverlaySize(width = 104.dp, height = 28.dp, textMaxWidth = 132.dp)
            basePosterWidthDp <= 132 -> CatalogLogoOverlaySize(width = 116.dp, height = 30.dp, textMaxWidth = 144.dp)
            else -> CatalogLogoOverlaySize(width = 128.dp, height = 34.dp, textMaxWidth = 156.dp)
        }
    } else {
        when {
            basePosterWidthDp <= 108 -> CatalogLogoOverlaySize(width = 72.dp, height = 18.dp, textMaxWidth = 92.dp)
            basePosterWidthDp <= 120 -> CatalogLogoOverlaySize(width = 80.dp, height = 20.dp, textMaxWidth = 104.dp)
            basePosterWidthDp <= 132 -> CatalogLogoOverlaySize(width = 88.dp, height = 22.dp, textMaxWidth = 112.dp)
            else -> CatalogLogoOverlaySize(width = 96.dp, height = 24.dp, textMaxWidth = 124.dp)
        }
    }

private fun KAPlayPosterShape.cardWidth(basePosterWidthDp: Int): Dp =
    when (this) {
        KAPlayPosterShape.Poster -> basePosterWidthDp.dp
        KAPlayPosterShape.Square -> basePosterWidthDp.dp
        KAPlayPosterShape.Landscape -> landscapePosterWidth(basePosterWidthDp)
    }
