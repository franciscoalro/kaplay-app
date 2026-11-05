package com.kaplay.app.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Scroll-aware state for the floating navigation bar.
 * Tracks scroll direction and exposes a label visibility fraction (1 = fully visible, 0 = hidden).
 */
@Stable
class KAPlayNavBarScrollState {
    /** 1f = labels fully visible (expanded), 0f = labels hidden (collapsed, icons only) */
    var labelVisibility by mutableFloatStateOf(1f)
        private set

    private var accumulatedDelta = 0f

    /** Call to expand (show labels) – e.g. when user scrolls back to top */
    fun expand() {
        labelVisibility = 1f
        accumulatedDelta = 0f
    }

    /** Call to collapse (hide labels) */
    fun collapse() {
        labelVisibility = 0f
        accumulatedDelta = 0f
    }

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val deltaY = available.y
            if (deltaY == 0f) return Offset.Zero

            accumulatedDelta += deltaY

            if (accumulatedDelta < -SCROLL_THRESHOLD && labelVisibility != 0f) {
                // Scrolling down past threshold → snap collapse
                labelVisibility = 0f
                accumulatedDelta = 0f
            } else if (accumulatedDelta > SCROLL_THRESHOLD && labelVisibility != 1f) {
                // Scrolling up past threshold → snap expand
                labelVisibility = 1f
                accumulatedDelta = 0f
            }

            // Reset accumulator if direction changed
            if (deltaY < 0f && accumulatedDelta > 0f) accumulatedDelta = deltaY
            if (deltaY > 0f && accumulatedDelta < 0f) accumulatedDelta = deltaY

            return Offset.Zero // Don't consume any scroll
        }
    }

    companion object {
        private const val SCROLL_THRESHOLD = 60f
    }
}

@Composable
fun rememberKAPlayNavBarScrollState(): KAPlayNavBarScrollState {
    return androidx.compose.runtime.remember { KAPlayNavBarScrollState() }
}

/**
 * Floating pill-shaped navigation bar with scroll-responsive labels.
 *
 * @param hazeState Optional [HazeState] whose source is placed on the content behind this bar.
 *                  When provided, the pill gets a blur-through effect.
 */
@Composable
fun KAPlayNavigationBar(
    modifier: Modifier = Modifier,
    scrollState: KAPlayNavBarScrollState? = null,
    hazeState: HazeState? = null,
    contentPadding: PaddingValues = floatingNavigationBarPadding(),
    compactSize: Boolean = false,
    content: @Composable KAPlayNavigationBarScope.() -> Unit,
) {
    val bottomInsets = kaplayBottomNavigationBarInsets().asPaddingValues()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(KAColors.Surface),
    ) {
        androidx.compose.material3.HorizontalDivider(
            thickness = 1.dp,
            color = KAColors.Outline,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = bottomInsets.calculateBottomPadding())
                .padding(horizontal = KASpacing.SM, vertical = KASpacing.SM),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KAPlayNavigationBarScopeImpl(
                rowScope = this,
                labelFraction = 1f,
                compactSize = compactSize,
            ).content()
        }
    }
}

interface KAPlayNavigationBarScope {
    @Composable
    fun NavItem(
        selected: Boolean,
        onClick: () -> Unit,
        icon: ImageVector,
        contentDescription: String?,
        modifier: Modifier = Modifier,
        label: String? = null,
    )

    @Composable
    fun NavItem(
        selected: Boolean,
        onClick: () -> Unit,
        icon: DrawableResource,
        contentDescription: String?,
        modifier: Modifier = Modifier,
        label: String? = null,
    )

    @Composable
    fun NavItem(
        selected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        label: String? = null,
        content: @Composable () -> Unit,
    )
}

private class KAPlayNavigationBarScopeImpl(
    private val rowScope: androidx.compose.foundation.layout.RowScope,
    private val labelFraction: Float,
    private val compactSize: Boolean,
) : KAPlayNavigationBarScope {
    private val iconSize = if (compactSize) 22.dp else 24.dp
    private val itemVerticalPadding = if (compactSize) 2.dp else 4.dp

    @Composable
    override fun NavItem(
        selected: Boolean,
        onClick: () -> Unit,
        icon: ImageVector,
        contentDescription: String?,
        modifier: Modifier,
        label: String?,
    ) {
        val iconColor by animateColorAsState(
            targetValue = if (selected) KAColors.Primary else KAColors.TextSecondary,
            label = "nav_icon_color",
        )

        with(rowScope) {
            Column(
                modifier = modifier
                    .weight(1f)
                    .selectable(
                        selected = selected,
                        enabled = true,
                        role = Role.Tab,
                        onClick = onClick,
                    )
                    .padding(vertical = itemVerticalPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    modifier = Modifier.size(iconSize),
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = iconColor,
                )
                NavItemLabel(label = label, labelFraction = labelFraction, iconColor = iconColor, selected = selected, compactSize = compactSize)
            }
        }
    }

    @Composable
    override fun NavItem(
        selected: Boolean,
        onClick: () -> Unit,
        icon: DrawableResource,
        contentDescription: String?,
        modifier: Modifier,
        label: String?,
    ) {
        val iconColor by animateColorAsState(
            targetValue = if (selected) KAColors.Primary else KAColors.TextSecondary,
            label = "nav_icon_color",
        )

        with(rowScope) {
            Column(
                modifier = modifier
                    .weight(1f)
                    .selectable(
                        selected = selected,
                        enabled = true,
                        role = Role.Tab,
                        onClick = onClick,
                    )
                    .padding(vertical = itemVerticalPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    modifier = Modifier.size(iconSize),
                    painter = painterResource(icon),
                    contentDescription = contentDescription,
                    tint = iconColor,
                )
                NavItemLabel(label = label, labelFraction = labelFraction, iconColor = iconColor, selected = selected, compactSize = compactSize)
            }
        }
    }

    @Composable
    override fun NavItem(
        selected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier,
        label: String?,
        content: @Composable () -> Unit,
    ) {
        val iconColor by animateColorAsState(
            targetValue = if (selected) KAColors.Primary else KAColors.TextSecondary,
            label = "nav_icon_color",
        )

        with(rowScope) {
            Column(
                modifier = modifier
                    .weight(1f)
                    .selectable(
                        selected = selected,
                        enabled = true,
                        role = Role.Tab,
                        onClick = onClick,
                    )
                    .padding(vertical = itemVerticalPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (compactSize) {
                    Box(Modifier.size(iconSize), contentAlignment = Alignment.Center) { content() }
                } else {
                    content()
                }
                NavItemLabel(label = label, labelFraction = labelFraction, iconColor = iconColor, selected = selected, compactSize = compactSize)
            }
        }
    }
}

@Composable
private fun NavItemLabel(
    label: String?,
    labelFraction: Float,
    iconColor: Color,
    selected: Boolean,
    compactSize: Boolean,
) {
    if (label == null || labelFraction <= 0f) return
    Spacer(modifier = Modifier.height((if (compactSize) 2.dp else KAPlayTokens.Space.s3) * labelFraction))
    Box(
        modifier = Modifier
            .height(KAPlayTokens.Space.s14 * labelFraction)
            .alpha(labelFraction),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = KAPlayTokens.Type.labelXs,
                lineHeight = KAPlayTokens.LineHeight.labelXs,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = iconColor,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}


/**
 * Classic flat navigation bar — the original pre-pill implementation.
 * No floating pill, no labels, no scroll behavior. Simple icon row with a top divider.
 */
@Composable
fun KAPlayClassicNavigationBar(
    modifier: Modifier = Modifier,
    content: @Composable KAPlayNavigationBarScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(KAColors.Surface),
    ) {
        androidx.compose.material3.HorizontalDivider(
            thickness = 1.dp,
            color = KAColors.Outline,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(kaplayBottomNavigationBarInsets().asPaddingValues())
                .padding(horizontal = KASpacing.SM, vertical = kaplayBottomNavigationExtraVerticalPadding),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KAPlayClassicNavigationBarScopeImpl(this).content()
        }
    }
}

private class KAPlayClassicNavigationBarScopeImpl(
    private val rowScope: androidx.compose.foundation.layout.RowScope,
) : KAPlayNavigationBarScope {

    @Composable
    override fun NavItem(
        selected: Boolean,
        onClick: () -> Unit,
        icon: ImageVector,
        contentDescription: String?,
        modifier: Modifier,
        label: String?,
    ) {
        val iconColor by animateColorAsState(
            targetValue = if (selected) KAColors.Primary else KAColors.TextSecondary,
            label = "classic_nav_icon_color",
        )
        with(rowScope) {
            Icon(
                modifier = modifier
                    .weight(1f, fill = false)
                    .selectable(
                        selected = selected,
                        enabled = true,
                        role = Role.Tab,
                        onClick = onClick,
                    )
                    .padding(KASpacing.SM)
                    .size(24.dp),
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconColor,
            )
        }
    }

    @Composable
    override fun NavItem(
        selected: Boolean,
        onClick: () -> Unit,
        icon: DrawableResource,
        contentDescription: String?,
        modifier: Modifier,
        label: String?,
    ) {
        val iconColor by animateColorAsState(
            targetValue = if (selected) KAColors.Primary else KAColors.TextSecondary,
            label = "classic_nav_icon_color",
        )
        with(rowScope) {
            Icon(
                modifier = modifier
                    .weight(1f, fill = false)
                    .selectable(
                        selected = selected,
                        enabled = true,
                        role = Role.Tab,
                        onClick = onClick,
                    )
                    .padding(KASpacing.SM)
                    .size(24.dp),
                painter = painterResource(icon),
                contentDescription = contentDescription,
                tint = iconColor,
            )
        }
    }

    @Composable
    override fun NavItem(
        selected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier,
        label: String?,
        content: @Composable () -> Unit,
    ) {
        val tokens = MaterialTheme.kaplay
        with(rowScope) {
            Box(
                modifier = modifier
                    .widthIn(max = tokens.components.navItemMaxWidth)
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .clip(tokens.components.navItemShape)
                    .selectable(
                        selected = selected,
                        enabled = true,
                        role = Role.Tab,
                        onClick = onClick,
                    )
                    .padding(KAPlayTokens.Space.s10),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}
