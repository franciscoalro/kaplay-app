package com.kaplay.app.features.rewards

import platform.Foundation.NSUserDefaults

internal actual object RewardStorage {
    private const val PREFIX = "ad_free_until_"
    private val defaults = NSUserDefaults.standardUserDefaults

    private fun storageKey(userId: String, profileIndex: Int): String =
        PREFIX + userId + "_" + profileIndex

    actual fun key(userId: String, profileIndex: Int): String =
        PREFIX + userId + "_" + profileIndex

    actual fun loadUntil(userId: String, profileIndex: Int): Long =
        defaults.doubleForKey(storageKey(userId, profileIndex)).toLong()

    actual fun saveUntil(userId: String, profileIndex: Int, untilEpochMs: Long) {
        defaults.setDouble(untilEpochMs.toDouble(), forKey = storageKey(userId, profileIndex))
    }

    actual fun clear(userId: String, profileIndex: Int) {
        defaults.removeObjectForKey(storageKey(userId, profileIndex))
    }
}
