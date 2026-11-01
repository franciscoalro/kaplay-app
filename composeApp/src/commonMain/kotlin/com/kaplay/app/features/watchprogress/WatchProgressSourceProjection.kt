package com.kaplay.app.features.watchprogress

import com.kaplay.app.features.tracking.WatchProgressSource

internal fun projectWatchProgressSourceEntries(
    source: WatchProgressSource,
    kaplayEntries: Collection<WatchProgressEntry>,
    providerEntries: Collection<WatchProgressEntry>,
): List<WatchProgressEntry> = if (source.providerId == null) {
    kaplayEntries.toList()
} else {
    providerEntries.toList()
}
