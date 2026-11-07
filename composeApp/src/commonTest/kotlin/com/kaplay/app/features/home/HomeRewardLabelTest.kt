package com.kaplay.app.features.home

import com.kaplay.app.features.rewards.AdRewardState
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeRewardLabelTest {
    @Test
    fun `reward label exposes loading and retryable error states`() {
        assertEquals("Sincronizando…", homeRewardLabel(false, AdRewardState(syncing = true)))
        assertEquals("Tentar novamente", homeRewardLabel(false, AdRewardState(error = "offline")))
        assertEquals("🎁 1h grátis", homeRewardLabel(false, AdRewardState()))
        assertEquals("Entrar • 1h grátis", homeRewardLabel(true, AdRewardState()))
    }

    @Test
    fun `active reward remains clickable so it can explain the remaining benefit`() {
        assertEquals(true, homeRewardEnabled(AdRewardState(remainingMs = 60_000L)))
    }

    @Test
    fun `active reward label takes precedence over a refresh error`() {
        assertEquals(
            "✨ 5m sem anúncios",
            homeRewardLabel(
                requiresAuthentication = false,
                rewardState = AdRewardState(
                    remainingMs = 5L * 60_000L,
                    error = "offline",
                ),
            ),
        )
    }

    @Test
    fun `retry remains enabled when an existing reward has a sync error`() {
        assertEquals(true, homeRewardEnabled(AdRewardState(remainingMs = 60_000L, error = "offline")))
        assertEquals(false, homeRewardEnabled(AdRewardState(syncing = true)))
    }
}
