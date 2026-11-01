package com.kaplay.app.core.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerConfigurationTest {
    @Test
    fun emptyBackendIsNotConfigured() {
        assertFalse(
            ServerConfiguration(
                backendUrl = "",
                publishableKey = "",
                capabilities = ServerCapabilities(false, false),
                isCustom = false,
            ).isConfigured,
        )
    }

    @Test
    fun publicBackendWithKeyIsConfigured() {
        assertTrue(
            ServerConfiguration(
                backendUrl = "https://example.supabase.co",
                publishableKey = "key",
                capabilities = ServerCapabilities(false, false),
                isCustom = false,
            ).isConfigured,
        )
    }
}
