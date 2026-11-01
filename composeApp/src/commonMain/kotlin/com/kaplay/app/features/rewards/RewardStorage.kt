package com.kaplay.app.features.rewards

internal expect object RewardStorage {
    /** Stable deterministic key for userId + profileIndex, matching backend (user_id, profile_index). */
    fun key(userId: String, profileIndex: Int): String

    fun loadUntil(userId: String, profileIndex: Int): Long
    fun saveUntil(userId: String, profileIndex: Int, untilEpochMs: Long)
    fun clear(userId: String, profileIndex: Int)
}
