package com.kaplay.app.features.plugins

internal expect object PluginStorage {
    fun loadState(profileId: Int): String?
    fun saveState(profileId: Int, payload: String)
    fun hasScraperCode(profileId: Int, scraperId: String): Boolean
    fun loadScraperCode(profileId: Int, scraperId: String): String?
    fun saveScraperCode(profileId: Int, scraperId: String, code: String, overwrite: Boolean): Boolean
    fun loadScraperSettings(scraperId: String): String?
    fun saveScraperSettings(scraperId: String, payload: String)
}
