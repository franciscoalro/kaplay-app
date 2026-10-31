package com.kaplay.app.features.player

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import com.kaplay.app.features.trailer.YoutubeChunkedDataSourceFactory

internal object PlatformPlaybackDataSourceFactory {
    fun create(
        context: Context,
        sourceUrl: String,
        sourceAudioUrl: String?,
        defaultRequestHeaders: Map<String, String>,
        defaultResponseHeaders: Map<String, String>,
        useYoutubeChunkedPlayback: Boolean,
        useLongReadTimeout: Boolean = false,
        externalSubtitles: List<com.kaplay.app.features.streams.StreamSubtitle> = emptyList(),
    ): DataSource.Factory {
        val primary = PlaybackTopLevelRequest(sourceUrl, defaultRequestHeaders)
        val additional = buildList {
            sourceAudioUrl?.takeIf { it.isNotBlank() }?.let {
                add(PlaybackTopLevelRequest(it, defaultRequestHeaders))
            }
            externalSubtitles.forEach { subtitle ->
                if (subtitle.url.isNotBlank()) {
                    add(PlaybackTopLevelRequest(subtitle.url, subtitle.headers.orEmpty()))
                }
            }
        }
        val secureFactory = PlayerPlaybackNetworking.createHttpDataSourceFactory(
            primary = primary,
            additional = additional,
            defaultHeaders = defaultRequestHeaders,
            useLongReadTimeout = useLongReadTimeout,
        )
        val networkFactory: DataSource.Factory = if (useYoutubeChunkedPlayback) {
            YoutubeChunkedDataSourceFactory(
                upstreamFactory = secureFactory,
            )
        } else {
            secureFactory
        }
        val subtitleHeaderFactory = SubtitleRequestHeaderDataSourceFactory(
            upstreamFactory = networkFactory,
            externalSubtitles = externalSubtitles,
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
