package com.kaplay.app.features.catalog

import com.kaplay.app.features.home.MetaPreview

/**
 * Categories are supplied by catalog metadata. Keep the first display spelling for
 * case variants while making the filter itself case-insensitive.
 */
internal fun catalogGenres(items: List<MetaPreview>): List<String> =
    items.asSequence()
        .flatMap { it.genres.asSequence() }
        .map(String::trim)
        .filter(String::isNotEmpty)
        .associateBy { it.lowercase() }
        .values
        .sortedBy { it.lowercase() }

internal fun filterCatalogItems(
    items: List<MetaPreview>,
    selectedGenre: String?,
): List<MetaPreview> {
    val genre = selectedGenre?.trim()?.takeIf(String::isNotEmpty) ?: return items
    return items.filter { item ->
        item.genres.any { itemGenre -> itemGenre.trim().equals(genre, ignoreCase = true) }
    }
}
