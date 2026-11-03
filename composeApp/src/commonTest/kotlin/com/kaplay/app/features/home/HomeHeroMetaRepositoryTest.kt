package com.kaplay.app.features.home

import com.kaplay.app.features.details.MetaDetails
import com.kaplay.app.features.details.MetaExternalRating
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeHeroMetaRepositoryTest {

    private val preview = MetaPreview(
        id = "tt1",
        type = "series",
        name = "Show",
        imdbRating = "7.1",
        genres = listOf("Drama"),
        runtime = "52 min",
        status = "Continuing",
    )

    @Test
    fun `full meta fills hero fields over catalog values`() {
        val merged = preview.withHeroMeta(
            HomeHeroMeta(
                details = MetaDetails(
                    id = "tt1",
                    type = "series",
                    name = "Show",
                    imdbRating = "8.4",
                    genres = listOf("Crime"),
                    runtime = "58 min",
                    status = "Ended",
                    ageRating = "TV-MA",
                    language = "en",
                ),
            ),
        )

        assertEquals("8.4", merged.imdbRating)
        assertEquals(listOf("Crime"), merged.genres)
        assertEquals("58 min", merged.runtime)
        assertEquals("Ended", merged.status)
        assertEquals("TV-MA", merged.ageRating)
        assertEquals("en", merged.language)
    }

    @Test
    fun `catalog values stay when full meta has gaps`() {
        val merged = preview.withHeroMeta(
            HomeHeroMeta(
                details = MetaDetails(
                    id = "tt1",
                    type = "series",
                    name = "Show",
                    imdbRating = " ",
                    runtime = "",
                ),
            ),
        )

        assertEquals("7.1", merged.imdbRating)
        assertEquals(listOf("Drama"), merged.genres)
        assertEquals("52 min", merged.runtime)
        assertEquals("Continuing", merged.status)
        assertEquals(null, merged.ageRating)
    }

    @Test
    fun `ratings attach without full meta`() {
        val ratings = listOf(MetaExternalRating(source = "imdb", value = 8.4))

        val merged = preview.withHeroMeta(HomeHeroMeta(ratings = ratings))

        assertEquals(ratings, merged.externalRatings)
        assertEquals("7.1", merged.imdbRating)
        assertEquals("52 min", merged.runtime)
    }

    @Test
    fun `hero dedupe collapses episode-aware and regular previews for the same content`() {
        val regular = MetaPreview(
            id = "tomato:6839",
            type = "tv",
            name = "Koori no Jouheki",
        )
        val latestEpisode = regular.copy(
            name = "Koori no Jouheki — Episódio 12",
            identityKey = "tomato:6839:episode:37809",
        )

        assertEquals(
            listOf(regular),
            deduplicateHomeHeroItems(listOf(regular, latestEpisode)),
        )
    }
}
