package com.kaplay.app.features.watching.sync

import com.kaplay.app.core.network.SupabaseProvider
import com.kaplay.app.core.sync.putSyncOriginClientId
import com.kaplay.app.features.watchprogress.WatchProgressEntry
import com.kaplay.app.features.watchprogress.resolvedProgressKey
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

object SupabaseProgressSyncAdapter : ProgressSyncAdapter {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun getDeltaCursor(profileId: Int): Long {
        val params = buildJsonObject {
            put("p_profile_index", profileId)
        }
        return SupabaseProvider.client.postgrest
            .rpc("sync_get_watch_progress_delta_cursor_v2", params)
            .decodeAs<Long>()
    }

    override suspend fun pullDelta(
        profileId: Int,
        sinceEventId: Long,
        limit: Int,
    ): List<ProgressDeltaEvent> {
        val params = buildJsonObject {
            put("p_profile_index", profileId)
            put("p_since_event_id", sinceEventId)
            put("p_limit", limit)
        }
        val result = SupabaseProvider.client.postgrest.rpc("sync_pull_watch_progress_delta_v2", params)
        return result.decodeList<WatchProgressDeltaSyncEntry>().map { event ->
            ProgressDeltaEvent(
                eventId = event.eventId,
                operation = event.operation,
                progressKey = event.progressKey,
                contentId = event.contentId,
                contentType = event.contentType,
                videoId = event.videoId,
                season = event.season,
                episode = event.episode,
                position = event.position,
                duration = event.duration,
                lastWatched = event.lastWatched,
            )
        }
    }

    override suspend fun pull(
        profileId: Int,
        sinceLastWatched: Long?,
        limit: Int?,
    ): List<ProgressSyncRecord> {
        val serverEntries = collectKeysetPages(
            fetchPage = { cursor: ProgressSnapshotCursor?, pageLimit ->
                val effectiveLimit = limit?.coerceIn(1, supabaseSyncPageSize) ?: pageLimit
                val params = buildJsonObject {
                    put("p_profile_index", profileId)
                    put("p_limit", effectiveLimit)
                    when {
                        cursor != null -> {
                            put("p_after_last_watched", cursor.lastWatched)
                            put("p_after_progress_key", cursor.progressKey)
                        }
                    }
                }
                SupabaseProvider.client.postgrest
                    .rpc("sync_pull_watch_progress_v2", params)
                    .decodeList<WatchProgressSyncEntry>()
            },
            cursorOf = { entry ->
                ProgressSnapshotCursor(entry.lastWatched, entry.progressKey)
            },
        )
        val records = serverEntries.map { entry ->
            ProgressSyncRecord(
                progressKey = entry.progressKey,
                contentId = entry.contentId,
                contentType = entry.contentType,
                videoId = entry.videoId,
                season = entry.season,
                episode = entry.episode,
                position = entry.position,
                duration = entry.duration,
                lastWatched = entry.lastWatched,
            )
        }
        return filterProgressSyncRecords(records, sinceLastWatched, limit)
    }

    override suspend fun push(
        profileId: Int,
        entries: Collection<WatchProgressEntry>,
    ) {
        val syncEntries = entries.map { entry ->
            WatchProgressSyncEntry(
                contentId = entry.parentMetaId,
                contentType = entry.contentType,
                videoId = entry.videoId,
                season = entry.seasonNumber,
                episode = entry.episodeNumber,
                position = entry.lastPositionMs,
                duration = entry.durationMs,
                lastWatched = entry.lastUpdatedEpochMs,
                progressKey = entry.resolvedProgressKey(),
            )
        }
        forEachSyncMutationBatch(syncEntries) { batch ->
            val params = buildJsonObject {
                put("p_profile_index", profileId)
                put("p_entries", json.encodeToJsonElement(batch))
                putSyncOriginClientId()
            }
            SupabaseProvider.client.postgrest.rpc("sync_push_watch_progress_v2", params)
        }
    }

    override suspend fun delete(
        profileId: Int,
        entries: Collection<WatchProgressEntry>,
    ) {
        val progressKeys = entries.map { entry -> entry.resolvedProgressKey() }
        forEachSyncMutationBatch(progressKeys) { batch ->
            val params = buildJsonObject {
                put("p_profile_index", profileId)
                put("p_keys", json.encodeToJsonElement(batch))
                putSyncOriginClientId()
            }
            SupabaseProvider.client.postgrest.rpc("sync_delete_watch_progress_v2", params)
        }
    }

}

internal data class ProgressSnapshotCursor(
    val lastWatched: Long,
    val progressKey: String,
)

/**
 * Builds param object for sync_pull_watch_progress_v2 (v2 contract).
 *
 * v2: p_profile_index (UUID-scope, not v1's p_profile_id integer).
 * Cursor params p_after_last_watched / p_after_progress_key are paired
 * (both present or both absent) per the SQL CHECK constraint.
 * The v1 RPC sync_pull_watch_progress is NOT called.
 */
internal fun filterProgressSyncRecords(
    records: List<ProgressSyncRecord>,
    sinceLastWatched: Long?,
    limit: Int?,
): List<ProgressSyncRecord> = records.asSequence()
    .filter { sinceLastWatched == null || it.lastWatched > sinceLastWatched }
    .let { sequence -> limit?.let { sequence.take(it.coerceAtLeast(0)) } ?: sequence }
    .toList()

internal fun buildProgressPullParams(
    profileId: Int,
    sinceLastWatched: Long?,
    limit: Int?,
): JsonObject = buildJsonObject {
    put("p_profile_index", profileId)
    put("p_limit", limit?.let(::JsonPrimitive) ?: JsonNull)
    // `sinceLastWatched` is applied after the descending v2 snapshot is read;
    // it must not be encoded as an `after` cursor because v2 walks older rows.
    // Both cursor params remain absent on the first page.
}

@Serializable
private data class WatchProgressSyncEntry(
    @SerialName("content_id") val contentId: String,
    @SerialName("content_type") val contentType: String,
    @SerialName("video_id") val videoId: String,
    val season: Int? = null,
    val episode: Int? = null,
    val position: Long = 0,
    val duration: Long = 0,
    @SerialName("last_watched") val lastWatched: Long = 0,
    @SerialName("progress_key") val progressKey: String = "",
)

@Serializable
private data class WatchProgressDeltaSyncEntry(
    @SerialName("event_id") val eventId: Long,
    val operation: String,
    @SerialName("progress_key") val progressKey: String,
    @SerialName("content_id") val contentId: String = "",
    @SerialName("content_type") val contentType: String = "",
    @SerialName("video_id") val videoId: String = "",
    val season: Int? = null,
    val episode: Int? = null,
    val position: Long = 0,
    val duration: Long = 0,
    @SerialName("last_watched") val lastWatched: Long = 0,
)
