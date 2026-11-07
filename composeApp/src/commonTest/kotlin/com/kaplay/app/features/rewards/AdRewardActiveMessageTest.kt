package com.kaplay.app.features.rewards

import kotlin.test.Test
import kotlin.test.assertEquals

class AdRewardActiveMessageTest {
    @Test
    fun `active reward message uses the actual remaining duration`() {
        assertEquals(
            "Você já está sem anúncios. Restam 2 horas e 5 minutos de benefício.",
            activeAdRewardMessage(2L * 60L * 60_000L + 5L * 60_000L),
        )
        assertEquals(
            "Você já está sem anúncios. Restam 1 minuto de benefício.",
            activeAdRewardMessage(61_000L),
        )
    }

    @Test
    fun `active reward message handles less than one minute`() {
        assertEquals(
            "Você já está sem anúncios. Restam menos de 1 minuto de benefício.",
            activeAdRewardMessage(59_000L),
        )
    }
}
