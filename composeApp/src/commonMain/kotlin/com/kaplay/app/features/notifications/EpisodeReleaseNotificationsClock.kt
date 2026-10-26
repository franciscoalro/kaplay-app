package com.kaplay.app.features.notifications

internal expect object EpisodeReleaseNotificationsClock {
    fun isoDateFromEpochMs(epochMs: Long): String
}