package com.kaplay.app.features.details

import kotlin.test.Test
import kotlin.test.assertEquals

class SeriesSeasonSupportTest {

    @Test
    fun `normalize season number maps zero and null to specials`() {
        assertEquals(SPECIALS_SEASON_NUMBER, normalizeSeasonNumber(0))
        assertEquals(SPECIALS_SEASON_NUMBER, normalizeSeasonNumber(null))
        assertEquals(2, normalizeSeasonNumber(2))
    }

    @Test
    fun `sorted playable episodes place specials after numbered seasons`() {
        val meta = MetaDetails(
            id = "show",
            type = "series",
            name = "Show",
            videos = listOf(
                MetaVideo(id = "special-1", title = "Special 1", season = 0, episode = 1),
                MetaVideo(id = "season-2", title = "Episode 1", season = 2, episode = 1),
                MetaVideo(id = "season-1", title = "Episode 1", season = 1, episode = 1),
                MetaVideo(id = "special-2", title = "Special 2", season = 0, episode = 2),
            ),
        )

        assertEquals(
            listOf("season-1", "season-2", "special-1", "special-2"),
            meta.sortedPlayableEpisodes().map(MetaVideo::id),
        )
    }

    @Test
    fun `preferred episode only applies to its season`() {
        assertEquals(
            5,
            preferredEpisodeNumberForSeason(
                displayedSeasonNumber = 1,
                preferredSeasonNumber = 1,
                preferredEpisodeNumber = 5,
            ),
        )
        assertEquals(
            null,
            preferredEpisodeNumberForSeason(
                displayedSeasonNumber = 2,
                preferredSeasonNumber = 1,
                preferredEpisodeNumber = 5,
            ),
        )
    }

    @Test
    fun `grouped episodes preserve a large absolute-numbered catalog`() {
        val episodeCount = 1_500
        val meta = MetaDetails(
            id = "long-running-show",
            type = "series",
            name = "Long Running Show",
            videos = (episodeCount downTo 1).map { episodeNumber ->
                MetaVideo(
                    id = "episode-$episodeNumber",
                    title = "Episode $episodeNumber",
                    season = 1,
                    episode = episodeNumber,
                )
            },
        )

        val groupedEpisodes = meta.groupedEpisodesForDisplay()

        assertEquals(listOf(1), groupedEpisodes.keys.toList())
        assertEquals((1..episodeCount).toList(), groupedEpisodes.getValue(1).map(MetaVideo::episode))
    }

    @Test
    fun `grouped episodes deduplicate duplicate canonical episodes while preserving distinct episodes`() {
        val meta = MetaDetails(
            id = "kusuriya",
            type = "series",
            name = "The Apothecary Diaries",
            videos = listOf(
                MetaVideo(id = "tomato:6945:37521", title = "E01 · Maomao", season = 1, episode = 1, thumbnail = "https://cdn.example/thumb1.jpg"),
                MetaVideo(id = "tomato:6945:37522", title = "E01 · Maomao", season = 1, episode = 1, overview = "Maomao was kidnapped", thumbnail = "https://cdn.example/thumb1.jpg"),
                MetaVideo(id = "tomato:6945:37523", title = "E02 · Mad Men", season = 1, episode = 2),
                MetaVideo(id = "tomato:6945:37524", title = "E01 · Season 2 Opener", season = 2, episode = 1),
            ),
        )

        val grouped = meta.groupedEpisodesForDisplay()

        val season1 = grouped.getValue(1)
        assertEquals(2, season1.size, "Season 1 should only have 2 distinct episodes, but had ${season1.size}")
        assertEquals(listOf(1, 2), season1.map(MetaVideo::episode))
        assertEquals("tomato:6945:37521", season1[0].id)
        assertEquals("https://cdn.example/thumb1.jpg", season1[0].thumbnail)
        assertEquals("Maomao was kidnapped", season1[0].overview)
    }

    @Test
    fun `does not eliminate legitimate episodes with identical titles if season or episode differs`() {
        val meta = MetaDetails(
            id = "show",
            type = "series",
            name = "Show",
            videos = listOf(
                MetaVideo(id = "s1e1", title = "Pilot", season = 1, episode = 1),
                MetaVideo(id = "s1e2", title = "Pilot", season = 1, episode = 2),
            ),
        )

        val season1 = meta.groupedEpisodesForDisplay().getValue(1)
        assertEquals(2, season1.size)
        assertEquals(listOf(1, 2), season1.map(MetaVideo::episode))
    }
}
