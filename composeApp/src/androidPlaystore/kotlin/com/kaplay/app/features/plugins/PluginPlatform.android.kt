package com.kaplay.app.features.plugins

import android.content.Context

internal actual object PluginStorage {
    fun initialize(context: Context) = Unit
    actual fun loadState(profileId: Int): String? = null
    actual fun saveState(profileId: Int, payload: String) = Unit
    actual fun hasScraperCode(profileId: Int, scraperId: String): Boolean = false
    actual fun loadScraperCode(profileId: Int, scraperId: String): String? = null
    actual fun saveScraperCode(profileId: Int, scraperId: String, code: String, overwrite: Boolean): Boolean = false
    actual fun loadScraperSettings(scraperId: String): String? = null
    actual fun saveScraperSettings(scraperId: String, payload: String) = Unit
}
