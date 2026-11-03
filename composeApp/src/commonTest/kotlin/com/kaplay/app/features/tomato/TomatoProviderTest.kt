package com.kaplay.app.features.tomato

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
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
            """{"anime_details":{"anime_id":6945,"anime_name":"Even the Student Council Has Its Holes!"},"anime_seasons":[{"season_id":2256,"season_name":"(Legendado) Season I","season_number":1,"season_dubbed":0}]}""",
        ).jsonObject

        assertEquals("Even the Student Council Has Its Holes!", TomatoProvider.detailName(payload))
        assertEquals(
            "(Legendado) Season I",
            TomatoProvider.seasonFromJson(payload["anime_seasons"]!!.jsonArray[0].jsonObject, 0)?.name,
        )
        assertEquals(
            2256L,
            TomatoProvider.seasonFromJson(payload["anime_seasons"]!!.jsonArray[0].jsonObject, 0)?.seasonId,
        )
        assertEquals(
            null,
            TomatoProvider.seasonFromJson(
                json.parseToJsonElement("""{"season_name":"Season I"}""").jsonObject,
                0,
            ),
        )
        assertEquals(
            listOf(37521L),
            TomatoProvider.episodeItems(
                json.parseToJsonElement(
                    """{"data":[{"ep_id":37521,"ep_name":"Episódio 1","ep_number":1,"ep_season_id":2256}]}""",
                ).jsonObject,
            ).mapNotNull(TomatoProvider::episodeIdOf),
        )
        val episode = TomatoProvider.episodeFromJson(
            json.parseToJsonElement(
                """{"ep_id":37521,"ep_name":"Episódio 1","ep_number":1,"ep_thumbnail":"https://cdn.example/ep.jpg"}""",
            ).jsonObject,
        )
        assertEquals("Episódio 1", episode?.name)
        assertEquals("https://cdn.example/ep.jpg", episode?.thumbnail)
        assertEquals(1, episode?.number)
        assertEquals(
            1,
            TomatoProvider.episodeNumberOf(
                json.parseToJsonElement("""{"ep_id":37521,"ep_name":"Episódio 1","ep_number":1}""").jsonObject,
            ),
        )
        assertEquals(
            1,
            TomatoProvider.episodeTotalOf(
                json.parseToJsonElement("""{"data":{"episodes":1}}""").jsonObject,
            ),
        )
        assertEquals(
            null,
            TomatoProvider.episodeTotalOf(
                json.parseToJsonElement("""{"data":{"episodes":[{"ep_id":37521}]}}""").jsonObject,
            ),
        )
    }

    @Test
    fun episodeFromJsonAcceptsCamelCaseEpisodeAliases() {
        val episode = TomatoProvider.episodeFromJson(
            json.parseToJsonElement(
                """{"episode_id":5001,"episode_number":12,"episode_name":"12. The Final Battle","episode_thumbnail":"https://cdn.example/ep12.jpg"}""",
            ).jsonObject,
        )

        assertEquals(5001L, episode?.episodeId)
        assertEquals(12, episode?.number)
        assertEquals("12. The Final Battle", episode?.name)
        assertEquals("https://cdn.example/ep12.jpg", episode?.thumbnail)
    }

    @Test
    fun detailNameRejectsTechnicalTitle() {
        val payload = json.parseToJsonElement(
            """{"anime_details":{"anime_name":"Tomato #6945"}}""",
        ).jsonObject

        assertEquals("", TomatoProvider.detailName(payload))
    }

    @Test
    fun acceptsRealTomatoDisplayTitle() {
        assertEquals(
            "Even the Student Council Has Its Holes!",
            TomatoProvider.displayTitleOrNull("Even the Student Council Has Its Holes!"),
        )
    }

    @Test
    fun rejectsTechnicalTomatoTitles() {
        assertTrueTechnicalTitle("tomato:6945")
        assertTrueTechnicalTitle("Tomato #6945")
        assertNull(TomatoProvider.displayTitleOrNull(""))
        assertNull(TomatoProvider.displayTitleOrNull("tomato:6945"))
        assertNull(TomatoProvider.displayTitleOrNull("Tomato #6945"))
    }

    private fun assertTrueTechnicalTitle(value: String) {
        kotlin.test.assertTrue(TomatoProvider.isTechnicalTitle(value))
        assertFalse(TomatoProvider.displayTitleOrNull(value) != null)
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
        assertNull(TomatoProvider.parseAnimeId("other:6945"))
        assertNull(TomatoProvider.parseAnimeId("tomato:0"))
        assertNull(TomatoProvider.parseEpisodeId("tomato:6945:0"))
    }

    // ---------- episode feed metadata ----------

    @Test
    fun feedCardPreservesEpisodeMetadataWithSnakeCaseKeys() {
        val item = json.parseToJsonElement(
            """{
                "ep_anime_id": 6839,
                "ep_id": 37809,
                "anime_name": "Koori no Jouheki",
                "ep_number": 5,
                "ep_name": "5. O Espelho",
                "ep_thumbnail": "https://cdn.example/ep5.jpg",
                "thumbnail": "https://cdn.example/thumb.jpg"
            }""",
        ).jsonObject

        val episodeId = TomatoProvider.episodeIdOf(item)
        val episodeNumber = TomatoProvider.episodeNumberOf(item)
        val episodeName = TomatoProvider.episodeNameOf(item)
        val episodeThumbnail = TomatoProvider.episodeThumbnailOf(item)

        assertEquals(37809L, episodeId)
        assertEquals(5, episodeNumber)
        assertEquals("5. O Espelho", episodeName)
        // ep_thumbnail takes priority over generic thumbnail
        assertEquals("https://cdn.example/ep5.jpg", episodeThumbnail)
    }

    @Test
    fun feedCardPreservesEpisodeMetadataWithCamelCaseAliases() {
        val item = json.parseToJsonElement(
            """{
                "anime_id": 6839,
                "episode_id": 5001,
                "anime_name": "Shadow Chronicle",
                "episode_number": 12,
                "episode_name": "12. The Final Battle"
            }""",
        ).jsonObject

        assertEquals(5001L, TomatoProvider.episodeIdOf(item))
        assertEquals(12, TomatoProvider.episodeNumberOf(item))
        assertEquals("12. The Final Battle", TomatoProvider.episodeNameOf(item))
    }

    @Test
    fun feedCardThumbnailsFallbackToGenericThumbnail() {
        val item = json.parseToJsonElement(
            """{
                "ep_anime_id": 6839,
                "ep_id": 37810,
                "thumbnail": "https://cdn.example/thumb.jpg"
            }""",
        ).jsonObject

        assertEquals("https://cdn.example/thumb.jpg", TomatoProvider.episodeThumbnailOf(item))
    }

    @Test
    fun derivesEpisodeNumberFromLiveFeedEpisodeName() {
        val item = json.parseToJsonElement(
            """{"ep_id":37894,"ep_anime_id":6922,"anime_name":"Battle Spirits [Re] ZEKKAI NO KU","ep_name":"5. O Espelho","thumbnail":"https://cdn.example/live.jpg"}""",
        ).jsonObject

        assertEquals(5, TomatoProvider.episodeNumberOf(item))
        assertEquals("https://cdn.example/live.jpg", TomatoProvider.episodeThumbnailOf(item))
    }

    @Test
    fun episodeNameOfReturnsNullWhenNoNameField() {
        val item = json.parseToJsonElement("""{"ep_id": 1}""").jsonObject
        assertNull(TomatoProvider.episodeNameOf(item))
    }

    @Test
    fun episodeThumbnailOfReturnsNullWhenNoThumbnail() {
        val item = json.parseToJsonElement("""{"ep_id": 1}""").jsonObject
        assertNull(TomatoProvider.episodeThumbnailOf(item))
    }

    @Test
    fun tomatoTitleFromFeedItemPopulatesEpisodeFields() {
        val item = json.parseToJsonElement(
            """{
                "ep_anime_id": 6839,
                "ep_id": 37809,
                "anime_name": "Koori no Jouheki",
                "ep_number": 5,
                "ep_name": "5. O Espelho",
                "ep_thumbnail": "https://cdn.example/ep5.jpg",
                "thumbnail": "https://cdn.example/thumb.jpg"
            }""",
        ).jsonObject

        val title = requireNotNull(TomatoProvider.titleFromFeedItem(item))

        assertEquals(6839L, title.id)
        assertEquals("Koori no Jouheki", title.name)
        assertEquals("https://cdn.example/ep5.jpg", title.episodeThumbnail)
        assertEquals(5, title.episodeNumber)
        assertEquals("5. O Espelho", title.episodeName)
    }
}
