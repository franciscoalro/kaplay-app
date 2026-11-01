package com.kaplay.app.features.watchprogress

internal expect object WatchProgressClock {
    fun nowEpochMs(): Long
}
