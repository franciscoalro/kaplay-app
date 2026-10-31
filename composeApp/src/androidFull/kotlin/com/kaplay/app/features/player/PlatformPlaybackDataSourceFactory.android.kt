package com.kaplay.app.features.player

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import com.kaplay.app.features.trailer.YoutubeChunkedDataSourceFactory

internal object PlatformPlaybackDataSourceFactory {
    fun create(
        context: Context,
        defaultRequestHeaders: Map<String, String>,
        defaultResponseHeaders: Map<String, String>,
        useYoutubeChunkedPlayback: Boolean,
        useLongReadTimeout: Boolean = false,
        externalSubtitles: List<com.kaplay.app.features.streams.StreamSubtitle> = emptyList(),
        primary: PlaybackTopLevelRequest? = null,
        additional: List<PlaybackTopLevelRequest> = emptyList(),
    ): DataSource.Factory {
        val networkFactory: DataSource.Factory = if (useYoutubeChunkedPlayback) {
            YoutubeChunkedDataSourceFactory(defaultRequestHeaders = defaultRequestHeaders)
        } else if (primary != null) {
            val subsAsAdditional = externalSubtitles.mapNotNull { sub ->
                if (sub.url.isBlank()) null
                else PlaybackTopLevelRequest(url = sub.url, headers = sub.headers ?: emptyMap())
            }
            PlayerPlaybackNetworking.createHttpDataSourceFactory(
                primary = primary,
                additional = additional + subsAsAdditional,
                defaultHeaders = defaultRequestHeaders,
                useLongReadTimeout = useLongReadTimeout,
            )
        } else {
            PlayerPlaybackNetworking.createHttpDataSourceFactory(
                defaultRequestHeaders,
                useLongReadTimeout,
            )
        }
        val subtitleHeaderFactory = SubtitleRequestHeaderDataSourceFactory(
            upstreamFactory = networkFactory,
            externalSubtitles = externalSubtitles
        )
        val baseFactory: DataSource.Factory = DefaultDataSource.Factory(context, subtitleHeaderFactory)
        return if (defaultResponseHeaders.isEmpty()) {
            baseFactory
        } else {
            ResponseHeaderOverridingDataSourceFactory(
                upstreamFactory = baseFactory,
                defaultResponseHeaders = defaultResponseHeaders,
            )
        }
    }
}
