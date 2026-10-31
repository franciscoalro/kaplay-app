package com.kaplay.app.features.player

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate

class PlayerPlaybackNetworkingSecurityTest {
    @Test
    fun clientRejectsUntrustedHttpsCertificate() {
        val heldCertificate = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(heldCertificate)
            .build()

        MockWebServer().use { server ->
            server.useHttps(serverCertificates.sslSocketFactory(), tunnelProxy = false)
            server.enqueue(MockResponse().setBody("unexpected success"))
            server.start()

            val client = PlayerPlaybackNetworking.createPlaybackHttpClient(
                context = securityContext(),
                useLongReadTimeout = false,
            )
            val request = Request.Builder().url(server.url("/video.m3u8")).build()

            val failure = assertFailsWith<IOException> {
                client.newCall(request).execute().use { response ->
                    error("Untrusted HTTPS unexpectedly succeeded with ${response.code}")
                }
            }

            assertTrue(failure.message.orEmpty().isNotBlank())
            assertEquals(0, server.requestCount, "TLS failure must not fall back to another request")
        }
    }

    @Test
    fun clientUsesDefaultHostnameVerifier() {
        val defaultClient = OkHttpClient()
        val client = PlayerPlaybackNetworking.createPlaybackHttpClient(
            context = securityContext(),
            useLongReadTimeout = false,
        )

        assertSame(defaultClient.hostnameVerifier, client.hostnameVerifier)
        assertEquals(defaultClient.certificatePinner, client.certificatePinner)
    }

    @Test
    fun clientInstallsOnePlaybackSecurityNetworkInterceptor() {
        val client = PlayerPlaybackNetworking.createPlaybackHttpClient(
            context = securityContext(),
            useLongReadTimeout = false,
        )

        assertEquals(
            1,
            client.networkInterceptors.count { it is PlaybackRequestSecurityInterceptor },
        )
    }

    @Test
    fun longTimeoutClientPreservesPlatformTimeoutConfiguration() {
        val context = securityContext()
        val standard = PlayerPlaybackNetworking.createPlaybackHttpClient(
            context = context,
            useLongReadTimeout = false,
        )
        val longRead = PlayerPlaybackNetworking.createPlaybackHttpClient(
            context = context,
            useLongReadTimeout = true,
        )

        assertEquals(15_000, standard.readTimeoutMillis)
        assertEquals(65_000, longRead.readTimeoutMillis)
        assertEquals(standard.connectTimeoutMillis, longRead.connectTimeoutMillis)
        assertEquals(standard.writeTimeoutMillis, longRead.writeTimeoutMillis)
        assertSame(standard.hostnameVerifier, longRead.hostnameVerifier)
        assertSame(standard.sslSocketFactory, longRead.sslSocketFactory)
        assertEquals(
            standard.networkInterceptors.map { it::class },
            longRead.networkInterceptors.map { it::class },
        )
    }

    @Test
    fun factoryAcceptsResourceSpecificScopes() {
        val primary = PlaybackTopLevelRequest(
            url = "https://video.example/master.m3u8",
            headers = mapOf("Authorization" to "Bearer video"),
        )
        val additional = listOf(
            PlaybackTopLevelRequest(
                url = "https://audio.example/track.m3u8",
                headers = mapOf("Authorization" to "Bearer audio"),
            ),
            PlaybackTopLevelRequest(
                url = "https://subtitles.example/captions.vtt",
                headers = mapOf("Authorization" to "Bearer subtitle"),
            ),
        )

        val factory = PlayerPlaybackNetworking.createHttpDataSourceFactory(
            primary = primary,
            additional = additional,
            defaultHeaders = primary.headers,
            useLongReadTimeout = false,
        )

        assertTrue(factory.createDataSource() != null)
    }

    private fun securityContext(): PlaybackRequestSecurityContext =
        PlaybackRequestSecurityContext.create(
            primary = PlaybackTopLevelRequest(
                url = "https://video.example/master.m3u8",
                headers = mapOf("Authorization" to "Bearer video"),
            ),
        )
}
