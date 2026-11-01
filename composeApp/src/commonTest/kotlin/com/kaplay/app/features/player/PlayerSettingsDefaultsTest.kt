package com.kaplay.app.features.player

import com.kaplay.app.features.player.skip.AutoSkipSegmentType
import kotlin.test.Test
import kotlin.test.assertTrue

class PlayerSettingsDefaultsTest {
    @Test
    fun newProfilesAutoSkipOpeningsAndEndings() {
        val settings = PlayerSettingsUiState()
        assertTrue(settings.skipIntroEnabled)
        assertTrue(AutoSkipSegmentType.INTRO in settings.autoSkipSegmentTypes)
        assertTrue(AutoSkipSegmentType.OUTRO in settings.autoSkipSegmentTypes)
    }

    @Test
    fun newProfilesAutoPlayNextEpisode() {
        assertTrue(PlayerSettingsUiState().streamAutoPlayNextEpisodeEnabled)
    }
}
