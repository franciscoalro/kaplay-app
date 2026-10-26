package com.kaplay.app.core.storage

import android.content.Context

internal actual object PlatformLocalAccountDataCleaner {
    private val preferenceNames = listOf(
        "episode_shuffle",
        "kaplay_addons",
        "kaplay_library",
        "kaplay_library_display_settings",
        "kaplay_home_catalog_settings",
        "kaplay_player_settings",
        "torrent_settings",
        "kaplay_profile_cache",
        "kaplay_avatar_cache",
        "kaplay_profile_pin_cache",
        "kaplay_theme_settings",
        "kaplay_poster_card_style",
        "kaplay_debrid_settings",
        "kaplay_mdblist_settings",
        "kaplay_mdblist_auth",
        "kaplay_mdblist_sync",
        "kaplay_auth",
        "kaplay_trakt_auth",
        "kaplay_simkl_auth",
        "kaplay_simkl_sync",
        "kaplay_trakt_library",
        "kaplay_trakt_settings",
        "kaplay_watched",
        "kaplay_stream_link_cache",
        "kaplay_stream_badge_settings",
        "kaplay_continue_watching_preferences",
        "kaplay_cw_enrichment",
        "kaplay_episode_release_notifications",
        "kaplay_episode_release_notifications_platform",
        "kaplay_watch_progress",
        "kaplay_discover_selection",
        "kaplay_collection_mobile_settings",
        "kaplay_collections",
        "kaplay_plugins",
        "kaplay_member_access",
    )

    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    actual fun wipe() {
        val context = appContext ?: return
        preferenceNames.forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
        context.filesDir.resolve("kaplay_plugin_scrapers").deleteRecursively()
    }
}
