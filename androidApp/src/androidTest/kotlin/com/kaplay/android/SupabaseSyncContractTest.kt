package com.kaplay.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.HttpURLConnection
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SupabaseSyncContractTest {
    @Test
    fun isolatedBackendIsHttpsAndReachableWithoutProductionHost() {
        val args = InstrumentationRegistry.getArguments()
        val baseUrl = args.getString("isolated_base_url").orEmpty().trimEnd('/')
        assertTrue("isolated_base_url instrumentation argument is required", baseUrl.isNotEmpty())

        val uri = URI(baseUrl)
        assertEquals("https", uri.scheme.lowercase())
        assertTrue(
            "production Supabase is forbidden in integration tests",
            !uri.host.orEmpty().contains("iibureqvpurmbgqupnvl.supabase.co"),
        )

        val connection = (URI("$baseUrl/rest/v1/").toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        try {
            assertTrue("isolated backend did not respond", connection.responseCode in 200..499)
        } finally {
            connection.disconnect()
        }
    }
}
