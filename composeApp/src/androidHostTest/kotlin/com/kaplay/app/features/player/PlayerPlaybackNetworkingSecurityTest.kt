package com.kaplay.app.features.player

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
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
            assertEquals(1, server.requestCount, "TLS failure must not trigger a fallback request")
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

    @Test
    fun httpsToHttpsCrossOriginRedirectStripsSensitiveHeaders() {
        val held = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(held).build()
        val trustedCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(held.certificate)
            .build()

        MockWebServer().use { source ->
            MockWebServer().use { destination ->
                source.useHttps(serverCertificates.sslSocketFactory(), tunnelProxy = false)
                destination.useHttps(serverCertificates.sslSocketFactory(), tunnelProxy = false)
                source.start()
                destination.start()
                source.enqueue(
                    MockResponse()
                        .setResponseCode(302)
                        .setHeader("Location", destination.url("/segment.ts")),
                )
                destination.enqueue(MockResponse().setBody("ok"))
                val sourceUrl = source.url("/master.m3u8").toString()
                val context = PlaybackRequestSecurityContext.create(
                    PlaybackTopLevelRequest(
                        sourceUrl,
                        mapOf(
                            "Authorization" to "Bearer source",
                            "Referer" to "https://catalog.example/watch?token=secret",
                        ),
                    ),
                )
                val client = trustedClient(trustedCertificates, context)
                val response = client.newCall(
                    Request.Builder()
                        .url(sourceUrl)
                        .header("Authorization", "Bearer source")
                        .header("Referer", "https://catalog.example/watch?token=secret")
                        .build(),
                ).execute()

                assertEquals(200, response.code)
                response.close()
                source.takeRequest()
                val redirected = destination.takeRequest()
                assertNull(redirected.getHeader("Authorization"))
                assertNull(redirected.getHeader("Referer"))
            }
        }
    }

    @Test
    fun httpsToHttpRedirectIsRejectedBeforeHttpServerReceivesRequest() {
        val held = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(held).build()
        val trustedCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(held.certificate)
            .build()

        MockWebServer().use { https ->
            MockWebServer().use { http ->
                https.useHttps(serverCertificates.sslSocketFactory(), tunnelProxy = false)
                https.start()
                http.start()
                https.enqueue(
                    MockResponse()
                        .setResponseCode(302)
                        .setHeader("Location", http.url("/segment.ts")),
                )
                val sourceUrl = https.url("/master.m3u8").toString()
                val context = PlaybackRequestSecurityContext.create(
                    PlaybackTopLevelRequest(sourceUrl, mapOf("Authorization" to "Bearer source")),
                )
                val client = trustedClient(trustedCertificates, context)

                assertFailsWith<IOException> {
                    client.newCall(Request.Builder().url(sourceUrl).build()).execute().use { }
                }
                assertEquals(0, http.requestCount)
            }
        }
    }

    @Test
    fun trustedHttpsControlSucceeds() {
        val held = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(held).build()
        val trustedCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(held.certificate)
            .build()
        MockWebServer().use { server ->
            server.useHttps(serverCertificates.sslSocketFactory(), tunnelProxy = false)
            server.enqueue(MockResponse().setBody("valid"))
            server.start()
            val url = server.url("/valid.m3u8").toString()
            val context = PlaybackRequestSecurityContext.create(PlaybackTopLevelRequest(url))
            val response = trustedClient(trustedCertificates, context)
                .newCall(Request.Builder().url(url).build()).execute()
            assertEquals("valid", response.body.string())
            response.close()
        }
    }

    @Test
    fun trustedClientRejectsWrongHostname() {
        val held = HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(held).build()
        val trustedCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(held.certificate)
            .build()
        MockWebServer().use { server ->
            server.useHttps(serverCertificates.sslSocketFactory(), tunnelProxy = false)
            server.start()
            val validUrl = server.url("/valid.m3u8")
            val wrongHostUrl = validUrl.newBuilder().host("127.0.0.1").build()
            val context = PlaybackRequestSecurityContext.create(PlaybackTopLevelRequest(validUrl.toString()))
            val failure = assertFailsWith<IOException> {
                trustedClient(trustedCertificates, context)
                    .newCall(Request.Builder().url(wrongHostUrl).build()).execute().use { }
            }
            assertTrue(generateSequence<Throwable>(failure) { it.cause }.any {
                it.javaClass.simpleName.contains("SSL", ignoreCase = true) ||
                    it.javaClass.simpleName.contains("Hostname", ignoreCase = true)
            })
        }
    }

    private fun trustedClient(
        trustedCertificates: HandshakeCertificates,
        context: PlaybackRequestSecurityContext,
    ): OkHttpClient = PlayerPlaybackNetworking.createPlaybackHttpClient(context, false)
        .newBuilder()
        .sslSocketFactory(trustedCertificates.sslSocketFactory(), trustedCertificates.trustManager)
        .build()

    private fun securityContext(): PlaybackRequestSecurityContext =
        PlaybackRequestSecurityContext.create(
            primary = PlaybackTopLevelRequest(
                url = "https://video.example/master.m3u8",
                headers = mapOf("Authorization" to "Bearer video"),
            ),
        )
}
