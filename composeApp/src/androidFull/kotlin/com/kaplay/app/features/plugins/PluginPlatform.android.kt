package com.kaplay.app.features.plugins

import android.content.Context
import android.content.SharedPreferences

internal actual object PluginStorage {
    private const val preferencesName = "kaplay_plugins"
    private const val pluginsStateKey = "plugins_state"
    private const val scraperCodeDirectoryName = "kaplay_plugin_scrapers"

    private var preferences: SharedPreferences? = null
    private var scraperCodeStore: PluginScraperCodeFileStore? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        scraperCodeStore = PluginScraperCodeFileStore(context.filesDir.resolve(scraperCodeDirectoryName))
    }

    actual fun loadState(profileId: Int): String? =
        preferences?.getString("${pluginsStateKey}_$profileId", null)

    actual fun saveState(profileId: Int, payload: String) {
        preferences
            ?.edit()
            ?.putString("${pluginsStateKey}_$profileId", payload)
            ?.apply()
    }

    actual fun hasScraperCode(profileId: Int, scraperId: String): Boolean =
        scraperCodeStore?.contains(profileId, scraperId) == true

    actual fun loadScraperCode(profileId: Int, scraperId: String): String? =
        scraperCodeStore?.load(profileId, scraperId)

    actual fun saveScraperCode(
        profileId: Int,
        scraperId: String,
        code: String,
        overwrite: Boolean,
    ): Boolean = scraperCodeStore?.save(profileId, scraperId, code, overwrite) == true

    actual fun loadScraperSettings(scraperId: String): String? =
        preferences?.getString("settings_${scraperId}", null)

    actual fun saveScraperSettings(scraperId: String, payload: String) {
        preferences
            ?.edit()
            ?.putString("settings_${scraperId}", payload)
            ?.apply()
    }
}

internal fun currentPluginPlatform(): String = "android"

internal fun currentEpochMillis(): Long = System.currentTimeMillis()
