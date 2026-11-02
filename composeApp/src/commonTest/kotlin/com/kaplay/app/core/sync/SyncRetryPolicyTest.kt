package com.kaplay.app.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals

class SyncRetryPolicyTest {
    @Test
    fun `retry delay grows with bounded exponential backoff`() {
        assertEquals(1_000L, pendingSyncRetryDelayMs(0))
        assertEquals(2_000L, pendingSyncRetryDelayMs(1))
        assertEquals(4_000L, pendingSyncRetryDelayMs(2))
        assertEquals(8_000L, pendingSyncRetryDelayMs(3))
        assertEquals(30_000L, pendingSyncRetryDelayMs(8))
        assertEquals(30_000L, pendingSyncRetryDelayMs(99))
    }
}
