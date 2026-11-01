package com.kaplay.app.features.rewards

import android.content.Context

internal actual object RewardStorage {
    private const val PREFERENCES = "kaplay_rewards"
    private const val PREFIX = "ad_free_until_"
    private var preferences: android.content.SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    }

    actual fun loadUntil(userId: String): Long =
        preferences?.getLong(PREFIX + userId, 0L) ?: 0L

    actual fun saveUntil(userId: String, untilEpochMs: Long) {
        preferences?.edit()?.putLong(PREFIX + userId, untilEpochMs)?.apply()
    }

    actual fun clear(userId: String) {
        preferences?.edit()?.remove(PREFIX + userId)?.apply()
    }
}
