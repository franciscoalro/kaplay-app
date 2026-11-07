package com.kaplay.app.features.home

import com.kaplay.app.features.catalog.CatalogTarget
import com.kaplay.app.features.catalog.catalogGenres
import com.kaplay.app.features.catalog.filterCatalogItems
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeCategoryFiltersTest {
    @Test
    fun `category options are unique trimmed and alphabetized`() {
        val sections = listOf(
            section(
                key = "featured",
                items = listOf(
                    preview("one", genres = listOf("Drama", "  Ação")),
                    preview("two", genres = listOf("drama", "Comédia")),
                ),
            ),
        )

        assertEquals(
            listOf("Ação", "Comédia", "Drama"),
            homeCatalogGenres(sections),
        )
    }

    @Test
    fun `selected category keeps matching entries and removes empty sections`() {
        val sections = listOf(
            section(
                key = "mixed",
                items = listOf(
                    preview("drama", genres = listOf("Drama")),
                    preview("comedy", genres = listOf("Comédia")),
                ),
            ),
            section(
                key = "only-comedy",
                items = listOf(preview("comedy-2", genres = listOf("Comédia"))),
            ),
        )

        val filtered = filterHomeCatalogSections(sections, " drama ")

        assertEquals(listOf("mixed"), filtered.map { it.key })
        assertEquals(listOf("drama"), filtered.single().items.map { it.id })
    }

    @Test
    fun `null category leaves sections unchanged`() {
        val sections = listOf(section("featured", listOf(preview("one"))))

        assertEquals(sections, filterHomeCatalogSections(sections, null))
    }

    private fun section(key: String, items: List<MetaPreview>): HomeCatalogSection =
        HomeCatalogSection(
            key = key,
            title = key,
            subtitle = "",
            addonName = "Addon",
            target = CatalogTarget.Addon(
                manifestUrl = "https://example.com/manifest.json",
                contentType = "movie",
                catalogId = key,
            ),
            items = items,
        )

    private fun preview(id: String, genres: List<String> = emptyList()): MetaPreview =
        MetaPreview(id = id, type = "movie", name = id, genres = genres)
}
