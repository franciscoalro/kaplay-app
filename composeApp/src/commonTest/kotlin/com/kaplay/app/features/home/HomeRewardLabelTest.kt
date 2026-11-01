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
}
