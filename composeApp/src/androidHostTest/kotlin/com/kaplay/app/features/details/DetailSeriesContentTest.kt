package com.kaplay.app.features.details

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kaplay.app.core.ui.KAColors
import com.kaplay.app.core.ui.KAShapes
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DetailSeriesContentTest {
    @Test
    fun seriesContentTokensMatchDesignSystemSpec() {
        assertEquals(8.dp, KAShapes.ChipRadius)
        assertEquals(10.dp, KAShapes.CardRadius)
        assertEquals(Color(0xFF9729D7), KAColors.Primary)
        assertEquals(Color(0xFF23232B), KAColors.SurfaceVariant)
        assertEquals(Color(0xFF393941), KAColors.Outline)
    }
}
