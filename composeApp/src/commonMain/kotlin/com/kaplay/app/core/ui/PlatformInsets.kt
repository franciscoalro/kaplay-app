package com.kaplay.app.core.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal expect val kaplayPlatformExtraTopPadding: Dp
internal expect val kaplayPlatformExtraBottomPadding: Dp
internal expect val kaplayBottomNavigationExtraVerticalPadding: Dp
@Composable
internal expect fun kaplayBottomNavigationBarInsets(): WindowInsets

/** Physical display-safe top inset, excluding any enclosing native toolbar. */
@Composable
internal expect fun platformPhysicalTopInset(): Dp

@Composable
internal expect fun ResizeForKeyboard()

internal val LocalKAPlayBottomNavigationOverlayPadding = staticCompositionLocalOf { 0.dp }

internal val LocalBottomInsetsConsumed = staticCompositionLocalOf { false }

/** CompositionLocal providing the shared [KAPlayNavBarScrollState] so child screens can attach the nestedScrollConnection. */
val LocalKAPlayNavBarScrollState = staticCompositionLocalOf<KAPlayNavBarScrollState?> { null }

@Composable
internal fun kaplaySafeBottomPadding(extra: Dp = 0.dp): Dp {
	if (LocalBottomInsetsConsumed.current) return extra
	val navigationBarBottom = kaplayBottomNavigationBarInsets()
		.asPaddingValues()
		.calculateBottomPadding()
	return navigationBarBottom.coerceAtLeast(kaplayPlatformExtraBottomPadding) +
		LocalKAPlayBottomNavigationOverlayPadding.current +
		extra
}
