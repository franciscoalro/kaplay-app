package com.kaplay.app.features.player

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.kaplay.app.core.diagnostics.SentryNetworkBreadcrumbInterceptor
import com.kaplay.app.core.network.IPv4FirstDns
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal object PlayerPlaybackNetworking {
    internal const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    /** Platform TLS/hostname verification; no custom trust manager or verifier. */
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

    internal fun createPlaybackHttpClient(
        context: PlaybackRequestSecurityContext,
        useLongReadTimeout: Boolean,
    ): OkHttpClient = playbackHttpClient.newBuilder()
        .apply {
            if (useLongReadTimeout) readTimeout(65, TimeUnit.SECONDS)
        }
        .addNetworkInterceptor(PlaybackRequestSecurityInterceptor(context))
        .build()

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

    private fun Map<String, String>.headerValue(name: String): String? =
        entries.firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }?.value
}
