package com.kaplay.app.features.details

import androidx.compose.ui.unit.dp
import com.kaplay.app.features.details.components.detailHeroHeight
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DetailHeroCompactTest {
    @Test
    fun detailHeroHeightStaysInCompactBoundsOnPhones() {
        val phoneWidth = 390.dp
        val height = detailHeroHeight(phoneWidth, isTablet = false)

        assertTrue(
            height in 280.dp..340.dp,
            "Expected hero height between 280.dp and 340.dp on phone, but was $height"
        )
    }

    @Test
    fun detailHeroHeightClampsAtCeilingOnLargePhones() {
        val largePhoneWidth = 480.dp
        val height = detailHeroHeight(largePhoneWidth, isTablet = false)

        assertTrue(
            height <= 340.dp,
            "Expected hero height on large phone to clamp at 340.dp, but was $height"
        )
    }
}
