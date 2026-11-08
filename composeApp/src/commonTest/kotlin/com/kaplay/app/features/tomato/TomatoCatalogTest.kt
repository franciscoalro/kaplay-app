package com.kaplay.app.features.tomato

import com.kaplay.app.features.catalog.CatalogTarget
import com.kaplay.app.features.home.stableKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

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
    fun feedPagePreservesCategoryRowsBeyondEnrichmentLimitAndReportsRawCount() {
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
        assertEquals((1L..25L).toList(), page.items.map { it.id.removePrefix("tomato:").toLong() })
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

    @Test
    fun progressiveEnrichmentPublishesAllCardsInOriginalOrderWithStableIdentity() = runTest {
        val base = (1L..25L).map { id ->
            TomatoTitle(id = id, name = "Anime #$id", image = null)
        }
        val sections = listOf(TomatoProvider.FeedSectionPayload("em-alta", "Em alta", base))
        val updates = mutableListOf<List<TomatoProvider.FeedSectionPayload>>()
        var inFlight = 0
        var maxInFlight = 0
        val enricher = TomatoProgressiveEnricher(maxConcurrency = 3) { id ->
            inFlight++
            maxInFlight = maxOf(maxInFlight, inFlight)
            delay(1)
            inFlight--
            TomatoDetails(
                animeId = id,
                name = "Detalhe $id",
                description = null,
                poster = "https://cdn.example/$id.jpg",
                banner = null,
                tags = emptyList(),
                seasons = emptyList(),
            )
        }

        enricher.enrich(sections) { updates += it }

        assertEquals(3, maxInFlight)
        assertEquals(25, updates.size)
        val finalTitles = updates.last().single().items
        assertEquals((1L..25L).toList(), finalTitles.map { it.id })
        assertEquals((1L..25L).map { "Detalhe $it" }, finalTitles.map { it.name })
        assertEquals("https://cdn.example/25.jpg", finalTitles.last().image)
        assertEquals(
            (1L..25L).map { "tv:tomato:$it" },
            finalTitles.map { TomatoCatalog.metaPreview(it).stableKey() },
        )
    }

    @Test
    fun progressiveEnrichmentIsolatesOneDetailFailureAndKeepsOtherCards() = runTest {
        val sections = listOf(
            TomatoProvider.FeedSectionPayload(
                "em-alta",
                "Em alta",
                (1L..5L).map { TomatoTitle(it, "Anime #$it", null) },
            ),
        )
        val enricher = TomatoProgressiveEnricher(maxConcurrency = 2) { id ->
            if (id == 3L) error("detail unavailable")
            TomatoDetails(id, "Detalhe $id", null, "poster:$id", null, emptyList(), emptyList())
        }
        var last: List<TomatoProvider.FeedSectionPayload> = sections

        enricher.enrich(sections) { last = it }

        assertEquals((1L..5L).toList(), last.single().items.map { it.id })
        assertEquals("Anime #3", last.single().items[2].name)
        assertEquals("poster:5", last.single().items[4].image)
    }

    @Test
    fun progressiveEnrichmentStopsWhenTheRequestIsCancelled() = runTest {
        val sections = listOf(
            TomatoProvider.FeedSectionPayload("em-alta", "Em alta", listOf(TomatoTitle(1L, "Anime #1", null))),
        )
        val enricher = TomatoProgressiveEnricher(maxConcurrency = 1) {
            delay(10_000)
            null
        }
        var updates = 0
        val job = launch {
            enricher.enrich(sections) { updates++ }
        }

        job.cancelAndJoin()

        assertEquals(0, updates)
    }
}
