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

    @Test
    fun feedPageDoesNotTruncateASectionAtTwentyAndDoesNotInventPagination() {
        val titles = (1L..25L).map { id ->
            TomatoTitle(id = id, name = "Anime $id", image = null)
        }
        val page = TomatoCatalog.feedPageFromSections(
            sectionId = "aventura",
            sections = listOf(
                TomatoProvider.FeedSectionPayload("aventura", "Aventura", titles),
            ),
        )

        assertEquals(25, page.items.size)
        assertEquals(25, page.rawItemCount)
        assertEquals(null, page.nextSkip)
    }

    @Test
    fun searchPageDoesNotTruncateProviderResultsAtTwenty() {
        val titles = (1L..25L).map { id ->
            TomatoTitle(id = id, name = "Resultado $id", image = null)
        }

        val page = TomatoCatalog.searchPageFromTitles(titles)

        assertEquals(25, page.items.size)
        assertEquals(25, page.rawItemCount)
        assertEquals(null, page.nextSkip)
    }

    @Test
    fun unknownSectionDoesNotFallBackToTheGlobalFeed() {
        val emAlta = TomatoTitle(id = 1L, name = "Em alta", image = null)
        val aventura = TomatoTitle(id = 2L, name = "Aventura", image = null)
        val sections = listOf(
            TomatoProvider.FeedSectionPayload("em-alta", "Em alta", listOf(emAlta)),
            TomatoProvider.FeedSectionPayload("aventura", "Aventura", listOf(aventura)),
        )

        assertEquals(
            emptyList(),
            TomatoCatalog.titlesForSection(
                sectionId = "categoria-inexistente",
                sections = sections,
                globalItems = listOf(emAlta, aventura),
            ),
        )
    }

    @Test
    fun sectionSelectorAcceptsCanonicalHomeKeyAndNullUsesGlobalFeed() {
        val emAlta = TomatoTitle(id = 1L, name = "Em alta", image = null)
        val aventura = TomatoTitle(id = 2L, name = "Aventura", image = null)
        val sections = listOf(
            TomatoProvider.FeedSectionPayload("em-alta", "Em alta", listOf(emAlta)),
            TomatoProvider.FeedSectionPayload("aventura", "Aventura", listOf(aventura)),
        )
        val globalItems = listOf(emAlta, aventura)

        assertEquals(
            listOf(emAlta),
            TomatoCatalog.titlesForSection("tomato:em-alta", sections, globalItems),
        )
        assertEquals(globalItems, TomatoCatalog.titlesForSection(null, sections, globalItems))
    }
}
