package com.kaplay.app.features.rewards

import kotlin.test.Test
import kotlin.test.assertEquals

class AdRewardActiveMessageTest {
    @Test
    fun `active reward message uses the actual remaining duration`() {
        assertEquals(
            "Seu benefício está ativo. Aguarde o término do tempo para liberar mais uma hora. Restam 2 horas e 5 minutos.",
            activeAdRewardMessage(2L * 60L * 60_000L + 5L * 60_000L),
        )
        assertEquals(
            "Seu benefício está ativo. Aguarde o término do tempo para liberar mais uma hora. Restam 1 minuto.",
            activeAdRewardMessage(61_000L),
        )
    }

    @Test
    fun `active reward message handles less than one minute`() {
        assertEquals(
            "Seu benefício está ativo. Aguarde o término do tempo para liberar mais uma hora. Restam menos de 1 minuto.",
            activeAdRewardMessage(59_000L),
        )
    }
}
