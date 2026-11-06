package com.kaplay.app.features.details

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DetailVerticalEpisodeListTest {
    @Test
    fun defaultEpisodeCardStyleIsList() {
        val defaultState = MetaScreenSettingsUiState()
        assertEquals(MetaEpisodeCardStyle.List, defaultState.episodeCardStyle)
    }

    @Test
    fun effectiveEpisodeCardStyleIsListOnPhonesEvenIfHorizontalConfigured() {
        val styleOnPhone = resolveEffectiveEpisodeCardStyle(
            isTablet = false,
            configuredStyle = MetaEpisodeCardStyle.Horizontal,
        )
        assertEquals(MetaEpisodeCardStyle.List, styleOnPhone)
    }

    @Test
    fun effectiveEpisodeCardStylePreservesTabletConfiguredStyle() {
        val horizontalOnTablet = resolveEffectiveEpisodeCardStyle(
            isTablet = true,
            configuredStyle = MetaEpisodeCardStyle.Horizontal,
        )
        val listOnTablet = resolveEffectiveEpisodeCardStyle(
            isTablet = true,
            configuredStyle = MetaEpisodeCardStyle.List,
        )

        assertEquals(MetaEpisodeCardStyle.Horizontal, horizontalOnTablet)
        assertEquals(MetaEpisodeCardStyle.List, listOnTablet)
    }
}
