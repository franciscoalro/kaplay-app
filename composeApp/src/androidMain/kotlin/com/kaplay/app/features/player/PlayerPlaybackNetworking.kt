package com.kaplay.app.features.player

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.kaplay.app.core.diagnostics.SentryNetworkBreadcrumbInterceptor
import com.kaplay.app.core.network.IPv4FirstDns
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal object PlayerPlaybackNetworking {
    internal const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val DEFAULT_STREAM_HEADERS = mapOf(
        "User-Agent" to DEFAULT_USER_AGENT,
    )

    /** Standard playback HTTP client — platform TLS, no trust-all, no custom hostname verifier. */
    private val playbackHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(IPv4FirstDns())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .addInterceptor(SentryNetworkBreadcrumbInterceptor())
            .build()
    }

    /** Long-read variant for loopback/local streams. */
    private val loopbackPlaybackHttpClient: OkHttpClient by lazy {
        playbackHttpClient.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request()
                val requestChain = if (isLoopbackHost(request.url.host)) {
                    chain.withReadTimeout(65, TimeUnit.SECONDS)
                } else {
                    chain
                }
                requestChain.proceed(request)
            }
            .build()
    }

    // ─── Security-aware entry points ───────────────────────────────────────────

    /**
     * Creates an OkHttpClient with [PlaybackRequestSecurityInterceptor] installed as a
     * network interceptor, enforcing per-origin credential scoping on every exchange
     * including redirects and HLS/DASH child requests.
     *
     * The returned client uses platform-default TLS and hostname verification.
     */
    internal fun createPlaybackHttpClient(
        context: PlaybackRequestSecurityContext,
        useLongReadTimeout: Boolean,
    ): OkHttpClient {
        val base = if (useLongReadTimeout) loopbackPlaybackHttpClient else playbackHttpClient
        return base.newBuilder()
            .addInterceptor(SentryNetworkBreadcrumbInterceptor())
            .addNetworkInterceptor(PlaybackRequestSecurityInterceptor(context))
            .build()
    }

    internal fun createHttpDataSourceFactory(
        primary: PlaybackTopLevelRequest,
        additional: Iterable<PlaybackTopLevelRequest> = emptyList(),
        defaultHeaders: Map<String, String> = emptyMap(),
        useLongReadTimeout: Boolean = false,
    ): DataSource.Factory {
        val context = PlaybackRequestSecurityContext.create(primary, additional)
        val client = createPlaybackHttpClient(context, useLongReadTimeout)
        val mergedHeaders = sanitizeHeaders(defaultHeaders)
        return OkHttpDataSource.Factory(client).apply {
            setDefaultRequestProperties(mergedHeaders)
            if (mergedHeaders.headerValue("User-Agent") == null) {
                setUserAgent(DEFAULT_USER_AGENT)
            }
        }
    }

    internal fun createDataSourceFactory(
        context: Context,
        primary: PlaybackTopLevelRequest,
        additional: Iterable<PlaybackTopLevelRequest> = emptyList(),
        defaultHeaders: Map<String, String> = emptyMap(),
        useLongReadTimeout: Boolean = false,
    ): DataSource.Factory {
        return DefaultDataSource.Factory(
            context,
            createHttpDataSourceFactory(primary, additional, defaultHeaders, useLongReadTimeout),
        )
    }

    // ─── Backward-compatible overloads (delegate to security-aware entry points) ──

    fun createHttpDataSourceFactory(
        defaultHeaders: Map<String, String> = emptyMap(),
        useLongReadTimeout: Boolean = false,
    ): DataSource.Factory {
        val sanitized = sanitizeHeaders(defaultHeaders)
        // Delegate to security-aware overload with an empty primary URL;
        // this preserves existing call-site behavior while enforcing HTTP stripping.
        val primary = PlaybackTopLevelRequest(
            url = "",
            headers = sanitized.filterKeys { it.equals("Authorization", ignoreCase = true) },
        )
        return createHttpDataSourceFactory(primary, emptyList(), defaultHeaders, useLongReadTimeout)
    }

    fun createDataSourceFactory(
        context: Context,
        defaultHeaders: Map<String, String> = emptyMap(),
        useLongReadTimeout: Boolean = false,
    ): DataSource.Factory {
        return DefaultDataSource.Factory(
            context,
            createHttpDataSourceFactory(defaultHeaders, useLongReadTimeout),
        )
    }

    // ─── Private helpers ────────────────────────────────────────────────────────

    private fun sanitizeHeaders(headers: Map<String, String>): Map<String, String> =
        headers.mapNotNull { (rawKey, rawValue) ->
            val key = rawKey.trim()
            val value = rawValue.trim()
            if (key.isBlank() || value.isBlank() || key.equals("Range", ignoreCase = true)) {
                null
            } else {
                key to value
            }
        }.toMap()

    private fun isLoopbackHost(host: String): Boolean = when (host.lowercase()) {
        "127.0.0.1", "localhost", "::1" -> true
        else -> false
    }

    private fun Map<String, String>.headerValue(name: String): String? =
        entries.firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }?.value
}