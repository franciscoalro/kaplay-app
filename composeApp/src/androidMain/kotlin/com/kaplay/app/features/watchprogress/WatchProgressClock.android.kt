package com.kaplay.app.features.watchprogress

actual object WatchProgressClock {
    actual fun nowEpochMs(): Long = System.currentTimeMillis()
}
