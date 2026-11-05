package com.kaplay.app.features.details

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaplay.app.features.details.components.compactEpisodeHorizontalCardMetrics
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DetailEpisodeCardMetricsTest {
    @Test
    fun compactEpisodeCardWidthIsWithinDesiredBoundsOnPhones() {
        val phoneWidthDp = 390f
        val metrics = compactEpisodeHorizontalCardMetrics(phoneWidthDp, 8.dp)

        assertTrue(
            metrics.cardWidth in 220.dp..260.dp,
            "Expected episode card width between 220.dp and 260.dp, but was ${metrics.cardWidth}"
        )
        assertEquals(10.dp, metrics.itemSpacing)
        assertEquals(8.dp, metrics.cornerRadius)
        assertEquals(14.sp, metrics.titleTextSize)
        assertEquals(12.sp, metrics.metaTextSize)
    }

    @Test
    fun compactEpisodeCardHeightFollows16By9AspectRatio() {
        val phoneWidthDp = 390f
        val metrics = compactEpisodeHorizontalCardMetrics(phoneWidthDp, 8.dp)
        val expectedHeight = metrics.cardWidth * (9f / 16f)

        assertEquals(expectedHeight, metrics.cardHeight)
    }
}
