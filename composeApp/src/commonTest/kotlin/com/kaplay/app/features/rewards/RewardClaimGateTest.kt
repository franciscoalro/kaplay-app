package com.kaplay.app.features.rewards

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RewardClaimGateTest {
    @Test
    fun `only one rewarded claim may be in flight`() {
        val gate = RewardClaimGate()

        assertTrue(gate.tryEnter())
        assertFalse(gate.tryEnter())
        gate.leave()
        assertTrue(gate.tryEnter())
    }
}
