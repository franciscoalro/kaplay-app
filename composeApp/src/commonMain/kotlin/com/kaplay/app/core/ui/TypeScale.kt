package com.kaplay.app.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

data class KAPlayTypeScale(
    val labelXs: TextStyle,
    val labelSm: TextStyle,
    val bodySm: TextStyle,
    val bodyMd: TextStyle,
    val bodyLg: TextStyle,
    val titleSm: TextStyle,
    val titleMd: TextStyle,
    val titleLg: TextStyle,
    val displaySm: TextStyle,
    val displayMd: TextStyle,
)

internal val LocalKAPlayTypeScale = staticCompositionLocalOf {
    KAPlayTypeScale(
        labelXs = TextStyle(fontSize = KAPlayTokens.Type.labelXs, lineHeight = KAPlayTokens.LineHeight.labelXs, fontWeight = FontWeight.Medium),
        labelSm = TextStyle(fontSize = KAPlayTokens.Type.labelSm, lineHeight = KAPlayTokens.LineHeight.labelSm, fontWeight = FontWeight.Medium),
        bodySm = TextStyle(fontSize = KAPlayTokens.Type.bodySm, lineHeight = KAPlayTokens.LineHeight.bodySm, fontWeight = FontWeight.Normal),
        bodyMd = TextStyle(fontSize = KAPlayTokens.Type.bodyMd, lineHeight = KAPlayTokens.LineHeight.bodyMd, fontWeight = FontWeight.Normal),
        bodyLg = TextStyle(fontSize = KAPlayTokens.Type.bodyLg, lineHeight = KAPlayTokens.LineHeight.bodyLg, fontWeight = FontWeight.Medium),
        titleSm = TextStyle(fontSize = KAPlayTokens.Type.titleSm, lineHeight = KAPlayTokens.LineHeight.titleSm, fontWeight = FontWeight.Bold),
        titleMd = TextStyle(fontSize = KAPlayTokens.Type.titleMd, lineHeight = KAPlayTokens.LineHeight.titleMd, fontWeight = FontWeight.Bold),
        titleLg = TextStyle(fontSize = KAPlayTokens.Type.titleLg, lineHeight = KAPlayTokens.LineHeight.titleLg, fontWeight = FontWeight.Bold),
        displaySm = TextStyle(fontSize = KAPlayTokens.Type.displaySm, lineHeight = KAPlayTokens.LineHeight.displaySm, fontWeight = FontWeight.ExtraBold),
        displayMd = TextStyle(fontSize = KAPlayTokens.Type.displayMd, lineHeight = KAPlayTokens.LineHeight.displayMd, fontWeight = FontWeight.ExtraBold),
    )
}

val MaterialTheme.kaplayTypeScale: KAPlayTypeScale
    @Composable
    get() = LocalKAPlayTypeScale.current
