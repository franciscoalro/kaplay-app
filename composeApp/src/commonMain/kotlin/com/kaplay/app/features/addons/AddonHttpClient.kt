package com.kaplay.app.features.addons

internal suspend fun fetchAddonResponseText(
    url: String,
    forceRefresh: Boolean = false,
): String {
    val headers = buildMap {
        put("Accept", "application/json")
        // Cinemeta rejects the platform default empty User-Agent with 403.
        put("User-Agent", "KAPlay-AddonClient")
        if (forceRefresh) put("Cache-Control", "no-cache")
    }
    return httpGetTextWithHeaders(url = url, headers = headers)
}
