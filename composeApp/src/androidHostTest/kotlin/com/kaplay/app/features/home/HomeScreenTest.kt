package com.kaplay.app.features.home

import androidx.compose.ui.unit.dp
import com.kaplay.app.core.ui.DefaultPosterCardCornerRadiusDp
import com.kaplay.app.core.ui.KAColors
import com.kaplay.app.core.ui.KAShapes
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeScreenTest {
    @Test
    fun posterRadiusMatchesDesignSystemSpec() {
        assertEquals(8, DefaultPosterCardCornerRadiusDp)
        assertEquals(8.dp, KAShapes.PosterRadius)
        assertEquals(12.dp, KAShapes.BannerRadius)
    }

    @Test
    fun brandColorsRemainConsistent() {
        assertEquals(androidx.compose.ui.graphics.Color(0xFF9729D7), KAColors.Primary)
    }
}
