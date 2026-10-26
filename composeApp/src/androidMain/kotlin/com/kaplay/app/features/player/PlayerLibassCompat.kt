package com.kaplay.app.features.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.text.SubtitleParser
import com.kaplay.subtitles.SubtitleHandler
import com.kaplay.subtitles.SubtitleMatroskaExtractor
import com.kaplay.subtitles.SubtitleParserFactory
import com.kaplay.subtitles.SubtitleRenderersFactory
import java.util.Collections
import java.util.WeakHashMap

private val assHandlersByPlayer = Collections.synchronizedMap(WeakHashMap<ExoPlayer, SubtitleHandler>())

@OptIn(UnstableApi::class)
internal fun ExoPlayer.Builder.buildWithAssSupportCompat(
    context: Context,
    dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(context),
    extractorsFactory: ExtractorsFactory = DefaultExtractorsFactory(),
    renderersFactory: RenderersFactory = DefaultRenderersFactory(context)
): ExoPlayer {
    val assHandler = SubtitleHandler()
    val assSubtitleParserFactory = CompatAssSubtitleParserFactory(assHandler)
    val assExtractorsFactory = extractorsFactory.withAssMkvSupportCompat(
        subtitleParserFactory = assSubtitleParserFactory,
        assHandler = assHandler
    )

    val mediaSourceFactory = DefaultMediaSourceFactory(
        dataSourceFactory,
        assExtractorsFactory
    )
    mediaSourceFactory.setSubtitleParserFactory(assSubtitleParserFactory)

    // Wrap the renderers factory with SubtitleRenderersFactory to add a
    // NoSampleRenderer-based position fallback for tunneled playback.
    val assRenderersFactory = SubtitleRenderersFactory(assHandler, renderersFactory)
    val player = this
        .setMediaSourceFactory(mediaSourceFactory)
        .setRenderersFactory(assRenderersFactory)
        .build()

    assHandlersByPlayer[player] = assHandler
    assHandler.init(player)
    return player
}

internal fun ExoPlayer.getAssHandlerCompat(): SubtitleHandler? = assHandlersByPlayer[this]

@OptIn(UnstableApi::class)
internal fun ExoPlayer.releaseWithAssSupportCompat() {
    val assHandler = assHandlersByPlayer.remove(this)
    try {
        release()
    } finally {
        assHandler?.release()
    }
}

@OptIn(UnstableApi::class)
private class CompatAssSubtitleParserFactory(
    private val assHandler: SubtitleHandler
) : SubtitleParser.Factory {
    private val delegate = SubtitleParserFactory(assHandler)

    override fun supportsFormat(format: Format): Boolean {
        return delegate.supportsFormat(normalizeSsaFormat(format))
    }

    override fun getCueReplacementBehavior(format: Format): Int {
        return delegate.getCueReplacementBehavior(normalizeSsaFormat(format))
    }

    override fun create(format: Format): SubtitleParser {
        return delegate.create(normalizeSsaFormat(format))
    }

    private fun normalizeSsaFormat(format: Format): Format {
        val isSsaByCodecs = format.codecs == MimeTypes.TEXT_SSA
        val isSsaByMime = format.sampleMimeType == MimeTypes.TEXT_SSA
        if (isSsaByCodecs && !isSsaByMime) {
            return format.buildUpon()
                .setSampleMimeType(MimeTypes.TEXT_SSA)
                .build()
        }
        return format
    }
}

@OptIn(UnstableApi::class)
private fun ExtractorsFactory.withAssMkvSupportCompat(
    subtitleParserFactory: SubtitleParser.Factory,
    assHandler: SubtitleHandler
): ExtractorsFactory {
    return ExtractorsFactory {
        val extractors = createExtractors()
        extractors.forEachIndexed { index, extractor ->
            if (extractor.underlyingImplementation is MatroskaExtractor) {
                extractors[index] = extractor.replaceInnermostExtractor(
                    SubtitleMatroskaExtractor(subtitleParserFactory, assHandler),
                )
            }
        }
        extractors
    }
}
