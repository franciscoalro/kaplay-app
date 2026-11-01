package com.kaplay.app.features.watching.sync

import com.kaplay.app.features.watched.WatchedItem
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SupabaseSyncPagingTest {
    @Test
    fun `keyset paging collects more than one thousand entries without gaps`() = runTest {
        val source = (1..1_201).toList()
        val seenCursors = mutableListOf<Int?>()

        val result = collectKeysetPages<Int, Int>(pageSize = 500, fetchPage = { cursor, limit ->
            seenCursors += cursor
            source.filter { cursor == null || it > cursor }.take(limit)
        }, cursorOf = { it })

        assertEquals(source, result)
        assertEquals(listOf(null, 500, 1_000), seenCursors)
    }

    @Test
    fun `keyset paging rejects a cursor that does not advance`() = runTest {
        assertFailsWith<IllegalStateException> {
            collectKeysetPages<Int, Int>(pageSize = 2, fetchPage = { _, _ -> listOf(1, 1) }, cursorOf = { it })
        }
    }

    @Test
    fun `keyset paging propagates an intermediate page failure`() = runTest {
        var calls = 0
        assertFailsWith<IllegalStateException> {
            collectKeysetPages<Int, Int>(pageSize = 500, fetchPage = { cursor, limit ->
                calls += 1
                if (calls == 2) error("page failed")
                (1..1_200).filter { cursor == null || it > cursor }.take(limit)
            }, cursorOf = { it })
        }
        assertEquals(2, calls)
    }

    @Test
    fun `mutation batching preserves order and stops after failure`() = runTest {
        val batches = mutableListOf<List<Int>>()
        val error = assertFailsWith<IllegalStateException> {
            forEachSyncMutationBatch((1..1_201).toList(), batchSize = 500) { batch ->
                batches += batch
                if (batches.size == 2) error("second batch failed")
            }
        }

        assertEquals("second batch failed", error.message)
        assertEquals(listOf(500, 500), batches.map { it.size })
        assertEquals((1..1_000).toList(), batches.flatten())
    }

    @Test
    fun `watched delete key includes content type`() {
        val movie = WatchedItem(id = "same", type = "movie", name = "Movie", markedAtEpochMs = 1L).toDeleteKey()
        val series = WatchedItem(id = "same", type = "series", name = "Series", markedAtEpochMs = 1L).toDeleteKey()
        val encodedMovie = Json.encodeToString(movie)
        val encodedSeries = Json.encodeToString(series)

        assertEquals("movie", Json.parseToJsonElement(encodedMovie).jsonObject.getValue("content_type").jsonPrimitive.content)
        assertEquals("series", Json.parseToJsonElement(encodedSeries).jsonObject.getValue("content_type").jsonPrimitive.content)
        assertTrue(movie != series)
    }

    // Task 1 (v1/v2 progress contract): the v1 RPC sync_pull_watch_progress used
    // p_profile_id + p_since_last_watched semantics and is NOT a verified production
    // path. All progress pulls now go through the v2 RPC sync_pull_watch_progress_v2
    // (UUID-scope, p_profile_index). These tests lock the v2 param contract.

    @Test
    fun `pull v2 params use profile_index not v1 profile_id`() {
        val params = buildProgressPullParams(profileId = 7, sinceLastWatched = null, limit = null)

        assertTrue(
            params.containsKey("p_profile_index"),
            "v2 RPC requires p_profile_index (UUID-scope), got ${params.keys}",
        )
        assertTrue(
            !params.containsKey("p_profile_id"),
            "v2 RPC must not use v1 p_profile_id; got ${params.keys}",
        )
    }

    @Test
    fun `pull v2 cursor params are paired or both absent`() {
        // Null cursor (first page): both after_* params absent.
        val first = buildProgressPullParams(profileId = 7, sinceLastWatched = null, limit = null)
        assertEquals(
            first.containsKey("p_after_last_watched"),
            first.containsKey("p_after_progress_key"),
            "v2 cursor must pair p_after_last_watched + p_after_progress_key",
        )

        // Seeded cursor: both present, mirroring the SQL CHECK constraint.
        val seeded = buildProgressPullParams(profileId = 7, sinceLastWatched = 5_000, limit = 100)
        assertTrue(seeded.containsKey("p_after_last_watched"), "expected p_after_last_watched cursor")
        assertTrue(seeded.containsKey("p_after_progress_key"), "expected p_after_progress_key cursor")
    }

    @Test
    fun `pull v2 params preserve explicit limit and since`() {
        val params = buildProgressPullParams(profileId = 7, sinceLastWatched = 5_000, limit = 100)

        assertEquals(100, params.getValue("p_limit").toString().toInt())
        assertEquals(5_000L, params.getValue("p_after_last_watched").toString().toLong())
    }
}
