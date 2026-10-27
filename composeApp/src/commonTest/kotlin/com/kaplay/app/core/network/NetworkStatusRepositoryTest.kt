package com.kaplay.app.core.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkStatusRepositoryTest {
    @Test
    fun successfulAndClientResponsesMeanServerAnswered() {
        assertTrue(isReachableHttpStatus(200))
        assertTrue(isReachableHttpStatus(404))
        assertTrue(isReachableHttpStatus(499))
    }

    @Test
    fun serverFailuresAreNotReportedAsReachable() {
        assertFalse(isReachableHttpStatus(500))
        assertFalse(isReachableHttpStatus(503))
        assertFalse(isReachableHttpStatus(599))
    }
}
