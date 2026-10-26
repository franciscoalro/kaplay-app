package com.kaplay.app.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Typography
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import kaplay.composeapp.generated.resources.Res
import kaplay.composeapp.generated.resources.jetbrains_sans_bold
import kaplay.composeapp.generated.resources.jetbrains_sans_regular
import kaplay.composeapp.generated.resources.jetbrains_sans_semibold
import org.jetbrains.compose.resources.Font

val LocalAppTheme = staticCompositionLocalOf { AppTheme.WHITE }
val LocalThemePalette = staticCompositionLocalOf { ThemeColors.White }

val MaterialTheme.themePalette: ThemeColorPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalThemePalette.current

val MaterialTheme.appTheme: AppTheme
    @Composable
    @ReadOnlyComposable
    get() = LocalAppTheme.current

private fun contentColorFor(background: Color): Color =
    if (background.luminance() > 0.5f) Color(0xFF111111) else Color(0xFFF5F7F8)

private fun buildColorScheme(palette: ThemeColorPalette, amoled: Boolean = false) = darkColorScheme(
    primary = palette.secondary,
    onPrimary = palette.onSecondary,
    primaryContainer = palette.focusBackground,
    onPrimaryContainer = contentColorFor(palette.focusBackground),
    secondary = palette.secondaryVariant,
    onSecondary = palette.onSecondaryVariant,
    background = if (amoled) Color.Black else palette.background,
    onBackground = Color(0xFFF5F7F8),
    surface = palette.backgroundElevated,
    onSurface = Color(0xFFF5F7F8),
    surfaceVariant = palette.backgroundCard,
    onSurfaceVariant = Color(0xFF969CA3),
    outline = Color(0xFF252A2A),
    error = Color(0xFFE36A8A),
    onError = Color(0xFFFCE5EC),
)

private val JetBrainsSans: FontFamily
    @Composable
    get() = FontFamily(
        Font(Res.font.jetbrains_sans_bold, FontWeight.Bold, FontStyle.Normal),
        Font(Res.font.jetbrains_sans_semibold, FontWeight.SemiBold, FontStyle.Normal),
        Font(Res.font.jetbrains_sans_regular, FontWeight.Normal, FontStyle.Normal),
    )

private val KAPlayTypography: Typography
    @Composable
    get() = Typography(
        displayLarge = TextStyle(
            fontFamily = JetBrainsSans,
            fontSize = KAPlayTokens.Type.pageDisplay,
            lineHeight = KAPlayTokens.LineHeight.pageDisplay,
            fontWeight = FontWeight.Bold,
            letterSpacing = KAPlayTokens.LetterSpacing.pageDisplay,
        ),
        headlineLarge = TextStyle(
            fontFamily = JetBrainsSans,
            fontSize = KAPlayTokens.Type.headline,
            lineHeight = KAPlayTokens.LineHeight.headline,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = KAPlayTokens.LetterSpacing.headline,
        ),
        titleLarge = TextStyle(
            fontFamily = JetBrainsSans,
            fontSize = KAPlayTokens.Type.titleSm,
            lineHeight = KAPlayTokens.LineHeight.materialTitleLarge,
            fontWeight = FontWeight.SemiBold,
        ),
        titleMedium = TextStyle(
            fontFamily = JetBrainsSans,
            fontSize = KAPlayTokens.Type.bodyLg,
            lineHeight = KAPlayTokens.LineHeight.bodyMd,
            fontWeight = FontWeight.SemiBold,
        ),
        bodyLarge = TextStyle(
            fontFamily = JetBrainsSans,
            fontSize = KAPlayTokens.Type.bodyApp,
            lineHeight = KAPlayTokens.LineHeight.bodyApp,
            fontWeight = FontWeight.Normal,
        ),
        bodyMedium = TextStyle(
            fontFamily = JetBrainsSans,
            fontSize = KAPlayTokens.Type.bodyMd,
            lineHeight = KAPlayTokens.LineHeight.bodyMd,
            fontWeight = FontWeight.Normal,
        ),
        labelLarge = TextStyle(
            fontFamily = JetBrainsSans,
            fontSize = KAPlayTokens.Type.bodyMd,
            lineHeight = KAPlayTokens.LineHeight.bodySm,
            fontWeight = FontWeight.SemiBold,
        ),
        labelMedium = TextStyle(
            fontFamily = JetBrainsSans,
            fontSize = KAPlayTokens.Type.labelSm,
            lineHeight = KAPlayTokens.LineHeight.labelXs,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = KAPlayTokens.LetterSpacing.label,
        ),
    )

private val KAPlayTypeTokens: KAPlayTypeScale
    @Composable
    get() = KAPlayTypeScale(
        labelXs = TextStyle(
            fontFamily = JetBrainsSans,
            fontSize = KAPlayTokens.Type.labelXs,
            lineHeight = KAPlayTokens.LineHeight.labelXs,
            fontWeight = FontWeight.SemiBold,
        ),
        labelSm = TextStyle(
            fontFamily = JetBrainsSans,
            fontSize = KAPlayTokens.Type.labelSm,
            lineHeight = KAPlayTokens.LineHeight.labelSm,
            fontWeight = FontWeight.SemiBold,
        ),
        bodySm = TextStyle(
            fontFamily = JetBrainsSans,
            fontSize = KAPlayTokens.Type.bodySm,
            lineHeight = KAPlayTokens.LineHeight.bodySm,
            fontWeight = FontWeight.Normal,
        ),
        bodyMd = TextStyle(
            fontFamily = JetBrainsSans,
            fontSize = KAPlayTokens.Type.bodyMd,
            lineHeight = KAPlayTokens.LineHeight.bodyMd,
            fontWeight = FontWeight.Normal,
        ),
        bodyLg = TextStyle(
            fontFamily = JetBrainsSans,
            fontSize = KAPlayTokens.Type.bodyLg,
            lineHeight = KAPlayTokens.LineHeight.bodyLg,
            fontWeight = FontWeight.Normal,
        ),
        titleSm = TextStyle(
            fontFamily = JetBrainsSans,
            fontSize = KAPlayTokens.Type.titleSm,
            lineHeight = KAPlayTokens.LineHeight.titleSm,
            fontWeight = FontWeight.SemiBold,
        ),
        titleMd = TextStyle(
            fontFamily = JetBrainsSans,
            fontSize = KAPlayTokens.Type.titleMd,
            lineHeight = KAPlayTokens.LineHeight.titleMd,
            fontWeight = FontWeight.SemiBold,
        ),
        titleLg = TextStyle(
            fontFamily = JetBrainsSans,
            fontSize = KAPlayTokens.Type.titleLg,
            lineHeight = KAPlayTokens.LineHeight.titleLg,
            fontWeight = FontWeight.SemiBold,
        ),
        displaySm = TextStyle(
            fontFamily = JetBrainsSans,
            fontSize = KAPlayTokens.Type.displaySm,
            lineHeight = KAPlayTokens.LineHeight.displaySm,
            fontWeight = FontWeight.Bold,
        ),
        displayMd = TextStyle(
            fontFamily = JetBrainsSans,
            fontSize = KAPlayTokens.Type.displayMd,
            lineHeight = KAPlayTokens.LineHeight.displayMd,
            fontWeight = FontWeight.Bold,
        ),
    )

private val KAPlayRippleConfiguration = RippleConfiguration(
    color = Color.Black,
)

@Composable
fun KAPlayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    appTheme: AppTheme = AppTheme.WHITE,
    amoled: Boolean = false,
    customThemeColors: CustomThemeColors = CustomThemeColors.Default,
    content: @Composable () -> Unit,
) {
    val palette = remember(appTheme, customThemeColors) {
        ThemeColors.getColorPalette(appTheme, customThemeColors)
    }
    val colorScheme = buildColorScheme(palette, amoled = amoled)
    val tokens = defaultKAPlayThemeTokens(palette, amoled = amoled, colorScheme = colorScheme)

    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = density.density,
            fontScale = 1f,
        ),
        LocalKAPlayThemeTokens provides tokens,
        LocalKAPlayTypeScale provides KAPlayTypeTokens,
        LocalRippleConfiguration provides KAPlayRippleConfiguration,
        LocalAppTheme provides appTheme,
        LocalThemePalette provides palette,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KAPlayTypography,
        ) {
            SkeletonAnimationProvider(content = content)
        }
    }
}
