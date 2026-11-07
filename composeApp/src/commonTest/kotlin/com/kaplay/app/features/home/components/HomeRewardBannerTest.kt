package com.kaplay.app.features.home.components

import com.kaplay.app.features.rewards.AdRewardState
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeRewardBannerTest {
    @Test
    fun `banner exposes authentication and active states`() {
        assertEquals(
            HomeRewardBannerContent(
                title = "Ganhe 1 hora sem anúncios",
                subtitle = "Entre na sua conta para assistir a um anúncio",
                actionLabel = "Entrar",
                isActionEnabled = true,
            ),
            homeRewardBannerContent(requiresAuthentication = true, rewardState = AdRewardState()),
        )
        assertEquals(
            HomeRewardBannerContent(
                title = "1 hora sem anúncios ativa",
                subtitle = "Tempo restante: 1h 5min",
                actionLabel = "Ativo",
                isActionEnabled = false,
            ),
            homeRewardBannerContent(
                requiresAuthentication = false,
                rewardState = AdRewardState(remainingMs = 65L * 60_000L),
            ),
        )
    }

    @Test
    fun `remaining time is compact and never shows zero minutes`() {
        assertEquals("1min", formatRewardRemaining(1L))
        assertEquals("59min", formatRewardRemaining(59L * 60_000L))
        assertEquals("2h", formatRewardRemaining(2L * 60L * 60_000L))
        assertEquals("2h 1min", formatRewardRemaining(121L * 60_000L))
    }
}
