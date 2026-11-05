package com.kaplay.app.features.home

import androidx.compose.ui.unit.dp
import com.kaplay.app.features.home.components.MOBILE_HERO_MAX_HEIGHT_DP
import com.kaplay.app.features.home.components.MOBILE_HERO_MIN_HEIGHT_DP
import com.kaplay.app.features.home.components.mobileHeroHeight
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeHeroBoundsTest {
    @Test
    fun heroConstantsMatchSpecRange() {
        assertEquals(260f, MOBILE_HERO_MIN_HEIGHT_DP)
        assertEquals(320f, MOBILE_HERO_MAX_HEIGHT_DP)
    }

    @Test
    fun mobileHeroHeightIsClampedToSpecBounds() {
        val phoneWidth = 390f
        val viewportHeight = 844f
        val height = mobileHeroHeight(phoneWidth, viewportHeight, null)

        assertTrue(
            height >= 260.dp && height <= 320.dp,
            "Expected hero height between 260.dp and 320.dp, but was $height"
        )
    }
}
