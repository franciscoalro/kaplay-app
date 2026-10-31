package com.kaplay.app.features.watching.sync

import com.kaplay.app.core.network.SupabaseProvider
import com.kaplay.app.core.sync.putSyncOriginClientId
import com.kaplay.app.features.watched.WatchedItem
import com.kaplay.app.features.watched.normalizeWatchedMarkedAtEpochMs
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

object SupabaseWatchedSyncAdapter : WatchedSyncAdapter {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun getDeltaCursor(profileId: Int): Long {
        val params = buildJsonObject {
            put("p_profile_index", profileId)
        }
        return SupabaseProvider.client.postgrest
            .rpc("sync_get_watched_items_delta_cursor_v2", params)
            .decodeAs<Long>()
    }

    override suspend fun pullDelta(
        profileId: Int,
        sinceEventId: Long,
        limit: Int,
    ): List<WatchedDeltaEvent> {
        val params = buildJsonObject {
            put("p_profile_index", profileId)
            put("p_since_event_id", sinceEventId)
            put("p_limit", limit)
        }
        val result = SupabaseProvider.client.postgrest.rpc("sync_pull_watched_items_delta_v2", params)
        return result.decodeList<WatchedDeltaSyncItem>().map { event ->
            WatchedDeltaEvent(
                eventId = event.eventId,
                operation = event.operation,
                contentId = event.contentId,
                contentType = event.contentType,
                title = event.title,
                season = event.season,
                episode = event.episode,
                watchedAt = event.watchedAt,
            )
        }
    }

    override suspend fun pull(
        profileId: Int,
        pageSize: Int,
    ): List<WatchedItem> {
        val serverItems = collectKeysetPages(
            pageSize = pageSize,
            fetchPage = { cursor: WatchedSnapshotCursor?, limit ->
                val params = buildJsonObject {
                    put("p_profile_index", profileId)
                    put("p_limit", limit)
                    cursor?.let {
                        put("p_after_watched_at", it.watchedAt)
                        put("p_after_content_id", it.contentId)
                        put("p_after_content_type", it.contentType)
                        put("p_after_season", it.season)
                        put("p_after_episode", it.episode)
                    }
                }
                SupabaseProvider.client.postgrest
                    .rpc("sync_pull_watched_items_v2", params)
                    .decodeList<WatchedSyncItem>()
            },
            cursorOf = WatchedSyncItem::snapshotCursor,
        )

        return serverItems.map { syncItem ->
            WatchedItem(
                id = syncItem.contentId,
                type = syncItem.contentType,
                name = syncItem.title,
                season = syncItem.season,
                episode = syncItem.episode,
                markedAtEpochMs = normalizeWatchedMarkedAtEpochMs(syncItem.watchedAt),
            )
        }
    }

    override suspend fun push(
        profileId: Int,
        items: Collection<WatchedItem>,
    ) {
        val syncItems = items.map { item ->
            WatchedSyncItem(
                contentId = item.id,
                contentType = item.type,
                title = item.name,
                season = item.season,
                episode = item.episode,
                watchedAt = normalizeWatchedMarkedAtEpochMs(item.markedAtEpochMs),
            )
        }
        forEachSyncMutationBatch(syncItems) { batch ->
            val params = buildJsonObject {
                put("p_profile_index", profileId)
                put("p_items", json.encodeToJsonElement(batch))
                putSyncOriginClientId()
            }
            SupabaseProvider.client.postgrest.rpc("sync_push_watched_items_v2", params)
        }
    }

    override suspend fun delete(
        profileId: Int,
        items: Collection<WatchedItem>,
    ) {
        val keys = items.map(WatchedItem::toDeleteKey)
        forEachSyncMutationBatch(keys) { batch ->
            val params = buildJsonObject {
                put("p_profile_index", profileId)
                put("p_keys", json.encodeToJsonElement(batch))
                putSyncOriginClientId()
            }
            SupabaseProvider.client.postgrest.rpc("sync_delete_watched_items_v2", params)
        }
    }
}

@Serializable
private data class WatchedSyncItem(
    @SerialName("content_id") val contentId: String,
    @SerialName("content_type") val contentType: String,
    val title: String = "",
    val season: Int? = null,
    val episode: Int? = null,
    @SerialName("watched_at") val watchedAt: Long = 0,
)

@Serializable
private data class WatchedDeltaSyncItem(
    @SerialName("event_id") val eventId: Long,
    val operation: String,
    @SerialName("content_id") val contentId: String,
    @SerialName("content_type") val contentType: String,
    val title: String = "",
    val season: Int? = null,
    val episode: Int? = null,
    @SerialName("watched_at") val watchedAt: Long = 0,
)

internal data class WatchedSnapshotCursor(
    val watchedAt: Long,
    val contentId: String,
    val contentType: String,
    val season: Int,
    val episode: Int,
)

private fun WatchedSyncItem.snapshotCursor(): WatchedSnapshotCursor =
    WatchedSnapshotCursor(
        watchedAt = watchedAt,
        contentId = contentId,
        contentType = contentType,
        season = season ?: -1,
        episode = episode ?: -1,
    )

@Serializable
internal data class WatchedDeleteKey(
    @SerialName("content_id") val contentId: String,
    @SerialName("content_type") val contentType: String,
    val season: Int? = null,
    val episode: Int? = null,
)

internal fun WatchedItem.toDeleteKey(): WatchedDeleteKey =
    WatchedDeleteKey(
        contentId = id,
        contentType = type,
        season = season,
        episode = episode,
    )
