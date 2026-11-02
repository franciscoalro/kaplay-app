package com.kaplay.app.core.sync

private const val PENDING_SYNC_RETRY_BASE_DELAY_MS = 1_000L
private const val PENDING_SYNC_RETRY_MAX_DELAY_MS = 30_000L

internal fun pendingSyncRetryDelayMs(attempt: Int): Long {
    val normalizedAttempt = attempt.coerceAtLeast(0).coerceAtMost(15)
    return (PENDING_SYNC_RETRY_BASE_DELAY_MS shl normalizedAttempt)
        .coerceAtMost(PENDING_SYNC_RETRY_MAX_DELAY_MS)
}
