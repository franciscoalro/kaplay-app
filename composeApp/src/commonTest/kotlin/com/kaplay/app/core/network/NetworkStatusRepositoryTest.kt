package com.kaplay.app.core.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkStatusRepositoryTest {
    @Test
    fun successfulAndClientResponsesMeanServerAnswered() {
        assertTrue(NetworkStatusRepository.isReachableHttpStatus(200))
        assertTrue(NetworkStatusRepository.isReachableHttpStatus(404))
        assertTrue(NetworkStatusRepository.isReachableHttpStatus(499))
    }

    @Test
    fun serverFailuresAreNotReportedAsReachable() {
        assertFalse(NetworkStatusRepository.isReachableHttpStatus(500))
        assertFalse(NetworkStatusRepository.isReachableHttpStatus(503))
        assertFalse(NetworkStatusRepository.isReachableHttpStatus(599))
    }
}
