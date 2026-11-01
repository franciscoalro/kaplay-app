package com.kaplay.app.features.watchprogress

import com.kaplay.app.features.tracking.WatchProgressSource
import kotlin.test.Test
import kotlin.test.assertEquals

class WatchProgressSourceProjectionTest {
    @Test
    fun `remote source excludes every KAPlay progress entry`() {
        val kaplayEntries = listOf(
            entry(parentMetaId = "shared", updatedAt = 200L),
            entry(parentMetaId = "kaplay-only", updatedAt = 300L),
        )
        val providerEntries = listOf(
            entry(parentMetaId = "shared", updatedAt = 100L),
        )

        listOf(WatchProgressSource.TRAKT, WatchProgressSource.SIMKL).forEach { source ->
            val projected = projectWatchProgressSourceEntries(
                source = source,
                kaplayEntries = kaplayEntries,
                providerEntries = providerEntries,
            )

            assertEquals(providerEntries, projected)
        }
    }

    @Test
    fun `KAPlay source excludes every provider progress entry`() {
        val kaplayEntries = listOf(entry(parentMetaId = "kaplay"))
        val providerEntries = listOf(entry(parentMetaId = "provider"))

        val projected = projectWatchProgressSourceEntries(
            source = WatchProgressSource.KAPLAY_SYNC,
            kaplayEntries = kaplayEntries,
            providerEntries = providerEntries,
        )

        assertEquals(kaplayEntries, projected)
    }

    private fun entry(
        parentMetaId: String,
        updatedAt: Long = 1L,
    ): WatchProgressEntry = WatchProgressEntry(
        contentType = "series",
        parentMetaId = parentMetaId,
        parentMetaType = "series",
        videoId = "$parentMetaId:1:1",
        title = parentMetaId,
        seasonNumber = 1,
        episodeNumber = 1,
        lastPositionMs = 10L,
        durationMs = 100L,
        lastUpdatedEpochMs = updatedAt,
    )
}
