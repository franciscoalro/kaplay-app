package com.kaplay.app.features.downloads

internal expect object DownloadsClock {
    fun nowEpochMs(): Long
}
