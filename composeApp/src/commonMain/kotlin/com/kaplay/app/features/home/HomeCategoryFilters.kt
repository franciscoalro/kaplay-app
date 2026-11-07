package com.kaplay.app.features.home

import com.kaplay.app.features.catalog.catalogGenres
import com.kaplay.app.features.catalog.filterCatalogItems

internal fun homeCatalogGenres(sections: List<HomeCatalogSection>): List<String> =
    catalogGenres(sections.flatMap(HomeCatalogSection::items))

internal fun filterHomeCatalogSections(
    sections: List<HomeCatalogSection>,
    selectedGenre: String?,
): List<HomeCatalogSection> {
    if (selectedGenre.isNullOrBlank()) return sections
    return sections.mapNotNull { section ->
        filterCatalogItems(section.items, selectedGenre)
            .takeIf { it.isNotEmpty() }
            ?.let { filteredItems -> section.copy(items = filteredItems) }
    }
}
