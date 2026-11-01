package com.kaplay.app.features.tomato

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class TomatoProviderTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesCurrentFeedCardsWithOnlyAnimeId() {
        val payload = json.parseToJsonElement(
            """{"data":[{"type":3,"title":"Em alta","data":[{"anime_id":6945,"thumbnail":"https://cdn.example/1.jpg"},{"anime_id":6914,"thumbnail":"https://cdn.example/2.jpg"}]}]}""",
        ).jsonObject

        val cards = TomatoProvider.feedItems(payload["data"], mutableListOf())

        assertEquals(listOf(6945L, 6914L), cards.mapNotNull(TomatoProvider::animeIdOf))
    }

    @Test
    fun parsesNewEpisodeCardsByEpisodeAnimeId() {
        val payload = json.parseToJsonElement(
            """{"data":[{"title":"Novos episódios","data":[{"ep_id":37809,"ep_anime_id":6839,"anime_name":"Koori no Jouheki","thumbnail":"https://cdn.example/new.jpg"}]}]}""",
        ).jsonObject

        val cards = TomatoProvider.feedItems(payload["data"], mutableListOf())

        assertEquals(listOf(6839L), cards.mapNotNull(TomatoProvider::animeIdOf))
    }

    @Test
    fun readsCurrentDetailEnvelope() {
        val payload = json.parseToJsonElement(
            """{"anime_details":{"anime_id":6945,"anime_name":"Even the Student Council Has Its Holes!"},"anime_seasons":[]}""",
        ).jsonObject

        assertEquals("Even the Student Council Has Its Holes!", TomatoProvider.detailName(payload))
    }

    @Test
    fun streamRequestsIncludeRequiredAppHeader() {
        assertEquals("1.4.3", TomatoProvider.apiHeaders("token", "stream")["x-app"])
    }

    @Test
    fun exposesMultipleBuiltinHomeCategories() {
        val definitions = TomatoCatalog.homeDefinitions()
        assertEquals(true, definitions.size > 1)
        assertEquals(true, definitions.any { it.catalogName == "Com dublagem" })
        assertEquals(true, definitions.any { it.catalogName == "Romance" })
    }

    @Test
    fun parsesTomatoIds() {
        assertEquals(6945L, TomatoProvider.parseAnimeId("tomato:6945"))
        assertEquals(123L, TomatoProvider.parseEpisodeId("tomato:6945:123"))
    }
}
