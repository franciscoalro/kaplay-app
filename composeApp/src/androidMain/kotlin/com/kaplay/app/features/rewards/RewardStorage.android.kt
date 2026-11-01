package com.kaplay.app.features.rewards

import android.content.Context

internal actual object RewardStorage {
    private const val PREFERENCES = "kaplay_rewards"
    private const val PREFIX = "ad_free_until_"
    private var preferences: android.content.SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    }

    actual fun key(userId: String, profileIndex: Int): String = "${PREFIX}${userId}_$profileIndex"

    actual fun loadUntil(userId: String, profileIndex: Int): Long =
        preferences?.getLong(key(userId, profileIndex), 0L) ?: 0L

    actual fun saveUntil(userId: String, profileIndex: Int, untilEpochMs: Long) {
        preferences?.edit()?.putLong(key(userId, profileIndex), untilEpochMs)?.apply()
    }

    actual fun clear(userId: String, profileIndex: Int) {
        preferences?.edit()?.remove(key(userId, profileIndex))?.apply()
    }
}
