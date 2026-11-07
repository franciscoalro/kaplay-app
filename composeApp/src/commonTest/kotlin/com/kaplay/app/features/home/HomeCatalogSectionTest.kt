package com.kaplay.app.features.home

import com.kaplay.app.features.catalog.CatalogTarget
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeCatalogSectionTest {
    @Test
    fun `can open catalog when page has more below preview limit`() {
        val section = section(
            availableItemCount = 11,
            hasMore = true,
        )

        assertTrue(section.canOpenCatalog(previewLimit = 18))
    }

    @Test
    fun `can open catalog when non paginated section exceeds preview limit`() {
        val section = section(
            availableItemCount = 19,
            hasMore = false,
        )

        assertTrue(section.canOpenCatalog(previewLimit = 18))
    }

    @Test
    fun `cannot open catalog when section fits preview and has no more pages`() {
        val section = section(
            availableItemCount = 11,
            hasMore = false,
        )

        assertFalse(section.canOpenCatalog(previewLimit = 18))
    }

    @Test
    fun `counts one five and twenty without inventing pagination`() {
        assertFalse(section(availableItemCount = 1, hasMore = false).canOpenCatalog(18))
        assertFalse(section(availableItemCount = 5, hasMore = false).canOpenCatalog(18))
        assertTrue(section(availableItemCount = 20, hasMore = false).canOpenCatalog(18))
    }

    @Test
    fun `paginated catalog with more than one hundred items remains navigable`() {
        assertTrue(section(availableItemCount = 18, hasMore = true).canOpenCatalog(18))
        assertTrue(section(availableItemCount = 105, hasMore = true).canOpenCatalog(18))
    }

    private fun section(
        availableItemCount: Int,
        hasMore: Boolean,
    ): HomeCatalogSection =
        HomeCatalogSection(
            key = "addon:movie:popular",
            title = "Popular",
            subtitle = "Addon",
            addonName = "Addon",
            target = CatalogTarget.Addon(
                manifestUrl = "https://example.com/manifest.json",
                contentType = "movie",
                catalogId = "popular",
                supportsPagination = hasMore,
            ),
            items = listOf(
                MetaPreview(
                    id = "tt1",
                    type = "movie",
                    name = "Movie",
                ),
            ),
            availableItemCount = availableItemCount,
            hasMore = hasMore,
        )
}
