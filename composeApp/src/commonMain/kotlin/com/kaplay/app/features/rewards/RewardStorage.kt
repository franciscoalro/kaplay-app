package com.kaplay.app.features.rewards

internal expect object RewardStorage {
    fun loadUntil(userId: String): Long
    fun saveUntil(userId: String, untilEpochMs: Long)
    fun clear(userId: String)
}
