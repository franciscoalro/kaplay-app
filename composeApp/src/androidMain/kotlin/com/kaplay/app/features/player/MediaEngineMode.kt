package com.kaplay.app.features.player

// Single binary-compatibility seam to the vendored media engine config.
import androidx.media3.common.NuvioEngineConfig

internal object MediaEngineMode {
    fun applyTorrentMode() {
        NuvioEngineConfig.set(NuvioEngineConfig.nuvioMode())
    }

    fun applyStockMode() {
        NuvioEngineConfig.set(NuvioEngineConfig.stockMode())
    }
}
