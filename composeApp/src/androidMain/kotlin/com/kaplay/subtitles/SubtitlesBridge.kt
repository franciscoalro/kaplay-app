package com.kaplay.subtitles

// Single binary-compatibility seam to the vendored libass AAR.
import com.nuvio.tv.libass.media.AssHandler
import com.nuvio.tv.libass.media.extractor.AssMatroskaExtractor
import com.nuvio.tv.libass.media.parser.AssSubtitleParserFactory
import com.nuvio.tv.libass.media.render.AssRenderersFactory
import com.nuvio.tv.libass.media.widget.AssSubtitleSurfaceView

typealias SubtitleHandler = AssHandler
typealias SubtitleMatroskaExtractor = AssMatroskaExtractor
typealias SubtitleParserFactory = AssSubtitleParserFactory
typealias SubtitleRenderersFactory = AssRenderersFactory
typealias SubtitleSurfaceView = AssSubtitleSurfaceView

internal object SubtitleRenderScale {
    fun set(scale: Float) {
        AssHandler.setRenderScale(scale)
    }
}
