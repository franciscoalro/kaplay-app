package com.kaplay.app.features.notifications

import android.content.Context
import android.content.SharedPreferences
import com.kaplay.app.core.storage.ProfileScopedKey

internal actual object EpisodeReleaseNotificationsStorage {
    private const val preferencesName = "kaplay_episode_release_notifications"
    private const val payloadKey = "episode_release_notifications_payload"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadPayload(): String? =
        preferences?.getString(ProfileScopedKey.of(payloadKey), null)

    actual fun savePayload(payload: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(payloadKey), payload)
            ?.apply()
    }
}