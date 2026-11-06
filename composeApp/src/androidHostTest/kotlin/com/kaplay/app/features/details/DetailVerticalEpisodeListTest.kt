package com.kaplay.app.features.details

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import com.kaplay.app.features.details.components.resolveEffectiveSeasonViewMode
import com.kaplay.app.features.details.components.shouldUseInlineSeasonHeader

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
    fun phoneAlwaysUsesTextSeasonSelectorEvenWhenPostersAreStored() {
        assertEquals(
            SeasonViewMode.Text,
            resolveEffectiveSeasonViewMode(
                isTablet = false,
                configuredMode = SeasonViewMode.Posters,
            ),
        )
    }

    @Test
    fun tabletPreservesConfiguredSeasonSelectorMode() {
        assertEquals(
            SeasonViewMode.Posters,
            resolveEffectiveSeasonViewMode(
                isTablet = true,
                configuredMode = SeasonViewMode.Posters,
            ),
        )
        assertEquals(
            SeasonViewMode.Text,
            resolveEffectiveSeasonViewMode(
                isTablet = true,
                configuredMode = SeasonViewMode.Text,
            ),
        )
    }

    @Test
    fun inlineSeasonHeaderFitsStandardPhoneWidth() {
        assertEquals(true, shouldUseInlineSeasonHeader(390f))
        assertEquals(false, shouldUseInlineSeasonHeader(359f))
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
