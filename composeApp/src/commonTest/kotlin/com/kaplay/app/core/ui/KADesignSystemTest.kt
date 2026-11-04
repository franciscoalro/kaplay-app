package com.kaplay.app.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class KADesignSystemTest {
    @Test
    fun colorsMatchDesignSystemSpec() {
        assertEquals(Color(0xFF0B0B0F), KAColors.Background)
        assertEquals(Color(0xFF19191F), KAColors.Surface)
        assertEquals(Color(0xFF23232B), KAColors.SurfaceVariant)
        assertEquals(Color(0xFF2C2C35), KAColors.SurfaceElevated)

        assertEquals(Color(0xFF9729D7), KAColors.Primary)
        assertEquals(Color(0xFF7B20B4), KAColors.PrimaryPressed)
        assertEquals(Color(0xFFC477E8), KAColors.PrimaryLight)

        assertEquals(Color(0xFFFFFFFF), KAColors.OnPrimary)
        assertEquals(Color(0xFFF5F5F7), KAColors.TextPrimary)
        assertEquals(Color(0xFFA3A3AD), KAColors.TextSecondary)
        assertEquals(Color(0xFF777781), KAColors.TextMuted)

        assertEquals(Color(0xFF393941), KAColors.Outline)
        assertEquals(Color(0xFF36B57B), KAColors.Success)
        assertEquals(Color(0xFFE7B34D), KAColors.Warning)
        assertEquals(Color(0xFFE46770), KAColors.Error)
    }

    @Test
    fun spacingMatchesDesignSystemSpec() {
        assertEquals(4.dp, KASpacing.XS)
        assertEquals(8.dp, KASpacing.SM)
        assertEquals(12.dp, KASpacing.MD)
        assertEquals(16.dp, KASpacing.LG)
        assertEquals(20.dp, KASpacing.XL)
        assertEquals(24.dp, KASpacing.XXL)
        assertEquals(28.dp, KASpacing.Section)
    }

    @Test
    fun shapesMatchDesignSystemSpec() {
        assertEquals(10.dp, KAShapes.ButtonRadius)
        assertEquals(12.dp, KAShapes.FieldRadius)
        assertEquals(10.dp, KAShapes.CardRadius)
        assertEquals(8.dp, KAShapes.PosterRadius)
        assertEquals(12.dp, KAShapes.BannerRadius)
        assertEquals(8.dp, KAShapes.ChipRadius)
        assertEquals(16.dp, KAShapes.ModalRadius)
    }
}
