package com.kaplay.app.features.player

import kotlin.test.Test
import kotlin.test.assertTrue

class VideoQualityTest {
    @Test
    fun autoIsTheSafeDefault() {
        assertTrue(VideoQuality.AUTO.isAuto)
        assertTrue(VideoQuality.AUTO.isSelected)
    }

    @Test
    fun fixedQualityIsNotAuto() {
        assertTrue(!VideoQuality(1080, "1080p").isAuto)
    }
}
