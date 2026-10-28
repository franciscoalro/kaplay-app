package com.kaplay.app.features.rewards

import platform.Foundation.NSUserDefaults

internal actual object RewardStorage {
    private const val PREFIX = "ad_free_until_"
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun loadUntil(userId: String): Long =
        defaults.doubleForKey(PREFIX + userId).toLong()

    actual fun saveUntil(userId: String, untilEpochMs: Long) {
        defaults.setDouble(untilEpochMs.toDouble(), forKey = PREFIX + userId)
    }

    actual fun clear(userId: String) {
        defaults.removeObjectForKey(PREFIX + userId)
    }
}
