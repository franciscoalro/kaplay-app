package com.kaplay.app.features.player

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import okhttp3.Request

class PlaybackRequestSecurityPolicyTest {
    @Test
    fun sameOriginHttpsRetainsRegisteredSensitiveAndCompatibilityHeaders() {
        val headers = mapOf(
            "Authorization" to "Bearer video",
            "Cookie" to "session=video",
            "User-Agent" to "KAPlay-Test",
            "Referer" to "https://catalog.example/",
            "Origin" to "https://catalog.example",
        )
        val context = context("https://media.example/master.m3u8", headers)
        val request = request("https://media.example/segment.ts", headers)

        val secured = context.secure(request)

        headers.forEach { (name, value) -> assertEquals(value, secured.header(name)) }
    }

    @Test
    fun sameOriginHttpsStripsUnregisteredSensitiveValue() {
        val context = context(
            "https://media.example/master.m3u8",
            mapOf("Authorization" to "Bearer registered"),
        )
        val request = request(
            "https://media.example/segment.ts",
            mapOf("Authorization" to "Bearer attacker", "User-Agent" to "KAPlay-Test"),
        )

        val secured = context.secure(request)

        assertNull(secured.header("Authorization"))
        assertEquals("KAPlay-Test", secured.header("User-Agent"))
    }

    @Test
    fun subtitleOriginAcceptsOnlySubtitleCredential() {
        val context = context(
            url = "https://video.example/master.m3u8",
            headers = mapOf("Authorization" to "Bearer video"),
            additional = listOf(
                PlaybackTopLevelRequest(
                    url = "https://subtitle.example/captions.vtt",
                    headers = mapOf("Authorization" to "Bearer subtitle"),
                ),
            ),
        )
        val request = Request.Builder()
            .url("https://subtitle.example/captions.vtt")
            .addHeader("Authorization", "Bearer video")
            .addHeader("Authorization", "Bearer subtitle")
            .header("User-Agent", "KAPlay-Test")
            .build()

        val secured = context.secure(request)

        assertEquals(listOf("Bearer subtitle"), secured.headers.values("Authorization"))
        assertEquals("KAPlay-Test", secured.header("User-Agent"))
    }

    @Test
    fun crossOriginHttpsStripsSensitiveHeadersOnly() {
        val registered = mapOf(
            "Authorization" to "Bearer video",
            "Cookie" to "session=video",
            "Proxy-Authorization" to "Basic proxy",
            "X-Api-Key" to "x-key",
            "Api-Key" to "api-key",
        )
        val compatibility = mapOf(
            "User-Agent" to "KAPlay-Test",
            "Referer" to "https://catalog.example/",
            "Origin" to "https://catalog.example",
        )
        val context = context("https://media.example/master.m3u8", registered)
        val secured = context.secure(request("https://cdn.example/segment.ts", registered + compatibility))

        registered.keys.forEach { name -> assertNull(secured.header(name), name) }
        compatibility.forEach { (name, value) -> assertEquals(value, secured.header(name), name) }
    }

    @Test
    fun differentPortIsNotAuthorizedButDefaultPortNormalizes() {
        val headers = mapOf("Authorization" to "Bearer video")
        val context = context("https://media.example/master.m3u8", headers)

        assertEquals(
            "Bearer video",
            context.secure(request("https://media.example:443/segment.ts", headers)).header("Authorization"),
        )
        assertNull(
            context.secure(request("https://media.example:8443/segment.ts", headers)).header("Authorization"),
        )
    }

    @Test
    fun httpAlwaysStripsSensitiveHeadersWithoutMutatingInput() {
        val sensitive = linkedMapOf(
            "authorization" to "Bearer video",
            "COOKIE" to "session=video",
            "Proxy-Authorization" to "Basic proxy",
            "x-api-key" to "x-key",
            "API-KEY" to "api-key",
        )
        val compatibility = mapOf(
            "User-Agent" to "KAPlay-Test",
            "Referer" to "http://catalog.example/",
            "Origin" to "http://catalog.example",
        )
        val context = context("http://media.example/master.m3u8", sensitive)
        val original = request("http://media.example/segment.ts", sensitive + compatibility)

        val secured = context.secure(original)

        sensitive.keys.forEach { name ->
            assertNull(secured.header(name), name)
            assertEquals(sensitive[name], original.header(name), "original $name")
        }
        compatibility.forEach { (name, value) -> assertEquals(value, secured.header(name), name) }
    }

    @Test
    fun httpRejectsUserInfoAndSensitiveQueryKeys() {
        val context = context("http://media.example/master.m3u8")
        val blocked = buildList {
            add("http://user:password@media.example/video.mp4")
            listOf(
                "token",
                "access_token",
                "auth",
                "authorization",
                "api_key",
                "apikey",
                "password",
                "passwd",
                "secret",
                "ToKeN",
            ).forEach { key -> add("http://media.example/video.mp4?$key=value") }
            add("http://media.example/video.mp4?access%5Ftoken=value")
            add("http://media.example/video.mp4?safe=1&token=first&token=second")
        }

        blocked.forEach { url ->
            assertFailsWith<IOException>(url) { context.secure(request(url)) }
        }
    }

    @Test
    fun httpAllowsCommonSignedMediaParameters() {
        val context = context("http://media.example/master.m3u8")
        val original = request("http://media.example/segment.ts?signature=abc&expires=123&key=media-id")

        val secured = context.secure(original)

        assertEquals(original.url, secured.url)
    }

    @Test
    fun httpsPrimaryRejectsHttpChildAndDowngrade() {
        val paths = listOf("master.m3u8", "segment.ts", "manifest.mpd", "chunk.m4s", "encryption.key")
        val explicitlyAllowed = "http://legacy.example/explicit.ts"
        val context = context(
            url = "https://media.example/master.m3u8",
            additional = listOf(PlaybackTopLevelRequest(explicitlyAllowed)),
        )

        paths.forEach { path ->
            assertFailsWith<IOException>(path) {
                context.secure(request("http://legacy.example/$path"))
            }
        }
        assertEquals(explicitlyAllowed, context.secure(request(explicitlyAllowed)).url.toString())
    }

    @Test
    fun httpPrimaryAllowsCredentialFreeHttpChildren() {
        val context = context("http://media.example/master.m3u8")
        val headers = mapOf("Authorization" to "Bearer video", "User-Agent" to "KAPlay-Test")

        listOf(
            "http://cdn.example/variant.m3u8",
            "http://cdn.example/segment.ts",
            "http://cdn.example/manifest.mpd",
            "http://cdn.example/chunk.m4s",
            "http://cdn.example/encryption.key",
        ).forEach { url ->
            val secured = context.secure(request(url, headers))
            assertNull(secured.header("Authorization"), url)
            assertEquals("KAPlay-Test", secured.header("User-Agent"), url)
        }
    }

    @Test
    fun adaptiveChildrenUseSameOriginPolicy() {
        val headers = mapOf("Authorization" to "Bearer video")
        val context = context("https://media.example/master.m3u8", headers)
        val paths = listOf("variant.m3u8", "segment.ts", "manifest.mpd", "chunk.m4s", "encryption.key")

        paths.forEach { path ->
            assertEquals(
                "Bearer video",
                context.secure(request("https://media.example/$path", headers)).header("Authorization"),
                "same-origin $path",
            )
            assertNull(
                context.secure(request("https://cdn.example/$path", headers)).header("Authorization"),
                "cross-origin $path",
            )
        }
    }

    @Test
    fun invalidAndBlankAdditionalUrlsAuthorizeNothing() {
        val headers = mapOf("Authorization" to "Bearer extra")
        val context = context(
            url = "https://media.example/master.m3u8",
            additional = listOf(
                PlaybackTopLevelRequest("", headers),
                PlaybackTopLevelRequest("not a URL", headers),
            ),
        )

        assertNull(
            context.secure(request("https://other.example/segment.ts", headers)).header("Authorization"),
        )
        assertFailsWith<IOException> {
            context.secure(request("http://other.example/segment.ts"))
        }
    }

    @Test
    fun interceptorAppliesContextBeforeProceeding() {
        val context = context("http://media.example/master.m3u8")
        val original = request(
            "http://media.example/segment.ts",
            mapOf("Authorization" to "Bearer video", "User-Agent" to "KAPlay-Test"),
        )
        var proceeded: Request? = null
        val chain = object : okhttp3.Interceptor.Chain {
            override fun request(): Request = original
            override fun proceed(request: Request): okhttp3.Response {
                proceeded = request
                throw ExpectedProceed
            }
            override fun connection(): okhttp3.Connection? = null
            override fun call(): okhttp3.Call = error("unused")
            override fun connectTimeoutMillis(): Int = 0
            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun readTimeoutMillis(): Int = 0
            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun writeTimeoutMillis(): Int = 0
            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        }

        assertSame(
            ExpectedProceed,
            assertFailsWith<IOException> { PlaybackRequestSecurityInterceptor(context).intercept(chain) },
        )
        assertNull(proceeded?.header("Authorization"))
        assertEquals("KAPlay-Test", proceeded?.header("User-Agent"))
    }

    private fun context(
        url: String,
        headers: Map<String, String> = emptyMap(),
        additional: Iterable<PlaybackTopLevelRequest> = emptyList(),
    ): PlaybackRequestSecurityContext = PlaybackRequestSecurityContext.create(
        primary = PlaybackTopLevelRequest(url, headers),
        additional = additional,
    )

    private fun request(url: String, headers: Map<String, String> = emptyMap()): Request =
        Request.Builder().url(url).apply {
            headers.forEach { (name, value) -> header(name, value) }
        }.build()

    private object ExpectedProceed : IOException("expected proceed")
}
