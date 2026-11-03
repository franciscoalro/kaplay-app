package com.kaplay.app.features.tomato

import com.kaplay.app.features.catalog.CatalogTarget
import com.kaplay.app.features.home.stableKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TomatoCatalogTest {
    @Test
    fun latestEpisodeLabelIncludesAnimeNumberAndCleanTitle() {
        val title = TomatoTitle(
            id = 6839L,
            name = "Koori no Jouheki",
            image = "https://cdn.example/episode.jpg",
            episodeId = 37809L,
            episodeNumber = 12,
            episodeName = "12. A muralha de gelo",
            episodeThumbnail = "https://cdn.example/episode.jpg",
        )

        assertEquals(
            "Koori no Jouheki — Episódio 12: A muralha de gelo",
            TomatoCatalog.latestEpisodeLabel(title),
        )
    }

    @Test
    fun latestEpisodeLabelFallsBackToSafeAnimeName() {
        val title = TomatoTitle(id = 6839L, name = "Koori no Jouheki", image = null)

        val label = TomatoCatalog.latestEpisodeLabel(title)

        assertEquals("Koori no Jouheki", label)
        assertFalse(label.contains("null", ignoreCase = true))
        assertFalse(label.contains("tomato:", ignoreCase = true))
    }

    @Test
    fun latestPreviewKeepsCanonicalIdAndEpisodeAwareStableKey() {
        val title = TomatoTitle(
            id = 6839L,
            name = "Koori no Jouheki",
            image = "https://cdn.example/episode.jpg",
            episodeId = 37809L,
            episodeNumber = 12,
            episodeName = "12. A muralha de gelo",
            episodeThumbnail = "https://cdn.example/episode.jpg",
        )

        val preview = TomatoCatalog.metaPreview(title, latestEpisode = true)

        assertEquals("tomato:6839", preview.id)
        assertEquals("Koori no Jouheki — Episódio 12: A muralha de gelo", preview.name)
        assertEquals("https://cdn.example/episode.jpg", preview.poster)
        assertEquals("tv:tomato:6839:episode:37809", preview.stableKey())
    }

    @Test
    fun tomatoCatalogTargetCarriesSelectedSection() {
        val target = CatalogTarget.Tomato(query = null, sectionId = "novos-episodios")

        assertEquals("novos-episodios", target.sectionId)
    }

    @Test
    fun regularPreviewDoesNotAddEpisodeLabelOrIdentity() {
        val title = TomatoTitle(
            id = 6839L,
            name = "Koori no Jouheki",
            image = "https://cdn.example/anime.jpg",
            episodeId = 37809L,
            episodeNumber = 12,
            episodeName = "12. A muralha de gelo",
        )

        val preview = TomatoCatalog.metaPreview(title)

        assertEquals("Koori no Jouheki", preview.name)
        assertEquals("tv:tomato:6839", preview.stableKey())
        assertTrue(preview.poster?.endsWith("anime.jpg") == true)
    }
}
