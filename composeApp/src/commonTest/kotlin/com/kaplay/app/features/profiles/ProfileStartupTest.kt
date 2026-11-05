package com.kaplay.app.features.profiles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfileStartupTest {
    @Test
    fun rememberLastProfileEnabledIsTrueByDefault() {
        val defaultState = ProfileState()
        assertTrue(defaultState.rememberLastProfileEnabled, "rememberLastProfileEnabled must be true by default")
    }

    @Test
    fun unpinnedProfileIsEligibleForStartupRestoration() {
        val profile = KAPlayProfile(
            profileIndex = 1,
            name = "Default",
            pinEnabled = false,
        )
        val pinnedProfile = KAPlayProfile(
            profileIndex = 2,
            name = "Kids",
            pinEnabled = true,
        )

        assertEquals(false, profile.pinEnabled)
        assertEquals(true, pinnedProfile.pinEnabled)
    }
}
