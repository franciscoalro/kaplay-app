package com.kaplay.app.features.addons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneralCatalogAddonTest {
    @Test
    fun `public general catalog exposes movie and series catalog meta only`() {
        val addon = generalCatalogAddon()
        val manifest = requireNotNull(addon.manifest)

        assertEquals(GENERAL_CATALOG_MANIFEST_URL, addon.manifestUrl)
        assertEquals(GENERAL_CATALOG_MANIFEST_ID, manifest.id)
        assertEquals(listOf("movie", "series"), manifest.types)
        assertEquals(listOf("tt"), manifest.idPrefixes)
        assertEquals(setOf("movie", "series"), manifest.catalogs.map { it.type }.toSet())
        assertTrue(manifest.resources.any { it.name == "catalog" })
        assertTrue(manifest.resources.any { it.name == "meta" })
        assertFalse(manifest.resources.any { it.name == "stream" })
    }

    @Test
    fun `general catalog keeps standard series type and imdb id contract`() {
        val manifest = requireNotNull(generalCatalogAddon().manifest)
        val series = manifest.catalogs.single { it.type == "series" }

        assertEquals("top", series.id)
        assertTrue(series.extra.any { it.name == "search" })
        assertTrue(series.extra.any { it.name == "skip" })
        assertTrue(manifest.supportsResource("meta", "series", "tt0944947"))
        assertFalse(manifest.supportsResource("meta", "tv", "tt0944947"))
        assertFalse(manifest.supportsResource("stream", "series", "tt0944947"))
    }

    @Test
    fun `virtual catalog is prepended for settings and home synchronization`() {
        val addon = ManagedAddon(
            manifestUrl = "https://example.com/manifest.json",
            manifest = null,
        )

        val addons = listOf(addon).withGeneralCatalog()

        assertEquals(GENERAL_CATALOG_MANIFEST_ID, addons.first().manifest?.id)
        assertEquals(addon, addons.last())
    }

    @Test
    fun `virtual catalog is not duplicated when already present`() {
        val addon = generalCatalogAddon()

        assertEquals(1, listOf(addon).withGeneralCatalog().count { it.manifest?.id == GENERAL_CATALOG_MANIFEST_ID })
    }
}
