package com.kaplay.app.features.watching.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SupabaseProgressSyncAdapterTest {
    @Test
    fun `filtered keyset results preserve only entries after timestamp and apply limit`() {
        val records = listOf(
            record("old", 100),
            record("equal", 200),
            record("new-a", 300),
            record("new-b", 400),
        )

        val result = filterProgressSyncRecords(records, sinceLastWatched = 200, limit = 1)

        assertEquals(listOf("new-a"), result.map { it.progressKey })
    }

    @Test
    fun `unfiltered results retain all records when no limit is supplied`() {
        val records = listOf(record("a", 100), record("b", 200))

        assertEquals(records, filterProgressSyncRecords(records, sinceLastWatched = null, limit = null))
    }

    @Test
    fun `non-positive limits are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            filterProgressSyncRecords(emptyList(), sinceLastWatched = null, limit = 0)
        }
    }

    private fun record(key: String, lastWatched: Long) = ProgressSyncRecord(
        contentId = key,
        contentType = "episode",
        videoId = key,
        lastWatched = lastWatched,
        progressKey = key,
    )
}
