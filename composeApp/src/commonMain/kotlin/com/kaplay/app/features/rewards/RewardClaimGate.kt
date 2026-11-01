package com.kaplay.app.features.rewards

import kotlinx.atomicfu.atomic

internal class RewardClaimGate {
    private val inFlight = atomic(false)

    fun tryEnter(): Boolean = inFlight.compareAndSet(expect = false, update = true)

    fun leave() {
        inFlight.value = false
    }
}
