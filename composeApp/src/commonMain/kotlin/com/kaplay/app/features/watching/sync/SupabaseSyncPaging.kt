package com.kaplay.app.features.watching.sync

internal const val supabaseSyncPageSize = 500
internal const val supabaseSyncMutationBatchSize = 500

internal suspend fun <T, C> collectKeysetPages(
    pageSize: Int = supabaseSyncPageSize,
    fetchPage: suspend (cursor: C?, limit: Int) -> List<T>,
    cursorOf: (T) -> C,
): List<T> {
    require(pageSize > 0)

    val items = mutableListOf<T>()
    var cursor: C? = null
    while (true) {
        val page = fetchPage(cursor, pageSize)
        items += page
        if (page.size < pageSize) return items

        val nextCursor = cursorOf(page.last())
        check(nextCursor != cursor) { "Sync page cursor did not advance" }
        cursor = nextCursor
    }
}

internal suspend fun <T> forEachSyncMutationBatch(
    values: Collection<T>,
    batchSize: Int = supabaseSyncMutationBatchSize,
    sendBatch: suspend (List<T>) -> Unit,
) {
    require(batchSize > 0)
    values.chunked(batchSize).forEach { batch -> sendBatch(batch) }
}
