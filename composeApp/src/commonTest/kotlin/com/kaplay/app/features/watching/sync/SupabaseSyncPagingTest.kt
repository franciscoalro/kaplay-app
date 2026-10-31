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

    @Test
    fun `legacy progress params include explicit nulls`() {
        val params = buildProgressPullParams(profileId = 7, sinceLastWatched = null, limit = null)

        assertEquals(setOf("p_profile_id", "p_since_last_watched", "p_limit"), params.keys)
        assertEquals("null", params.getValue("p_since_last_watched").toString())
        assertEquals("null", params.getValue("p_limit").toString())
    }
}
