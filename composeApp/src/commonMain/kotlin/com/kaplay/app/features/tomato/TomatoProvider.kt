package com.kaplay.app.features.tomato

import co.touchlab.kermit.Logger
import com.kaplay.app.core.network.createApiHttpClient
import com.kaplay.app.features.plugins.PluginStorage
import com.kaplay.app.features.streams.StreamItem
import com.kaplay.app.features.streams.StreamProxyHeaders
import com.kaplay.app.features.streams.StreamBehaviorHints
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlin.concurrent.Volatile
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Embedded Tomato provider: transport, token, retry policy and payload
 * mapping for the Tomato anime backend. Nothing above this file touches
 * hosts, headers or JSON shapes directly.
 */
object TomatoProvider {
    private val log = Logger.withTag("TomatoFeed")
    const val ANIME_ID_PREFIX = "tomato:"
    const val PROVIDER_NAME = "Tomato"

    internal const val SETTINGS_SCRAPER_ID = "builtin:tomato"
    internal const val SETTINGS_TOKEN_KEY = "bearerToken"

    // Same public session token shipped by the original CloudStream Tomato provider.
    // User settings still take precedence and can replace it when the session rotates.
    private const val DEFAULT_BEARER_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6NDg0NjcyOSwidXVpZCI6ImQ0ODk1NjZjLTI0NDMtNDU3OS1iMzkwLWI1YjQxOTgzMTA5MCIsImlhdCI6MTc5MDUwNjI4MH0.3MP87IJav4bhPJzt5YUUv1mEeOdWp2zxo5_hc6w51YU"

    private const val HOST_EDGE = "https://edge.betomato.com"
    private const val HOST_PROD = "https://prod-api.tomatoanimes.com"

    internal val maxAttemptsPerRequest = 6
    internal val retryDelayMillis = 150L
    private const val FEED_ENRICH_LIMIT = 20
    private val LATEST_EPISODE_SECTION_KEYS = setOf("novos-episodios", "ultimos-episodios", "latest-episodes")

    internal data class FeedSectionPayload(
        val key: String,
        val title: String,
        val items: List<TomatoTitle>,
        val sourceItemCount: Int = items.size,
    )

    private val feedCacheMutex = Mutex()
    @Volatile
    private var feedCache: List<FeedSectionPayload>? = null
    @Volatile
    private var feedCacheExpiresAtMillis: Long = 0L

    private val json = Json { ignoreUnknownKeys = true }

    sealed class TomatoException(message: String) : Exception(message) {
        class TokenMissing : TomatoException("Configure o token Tomato nas configurações")
        class RequestFailed(message: String) : TomatoException(message)
    }

    // ---------- token ----------

    fun configuredToken(): String {
        val raw = PluginStorage.loadScraperSettings(SETTINGS_SCRAPER_ID) ?: return DEFAULT_BEARER_TOKEN
        val payload = runCatching {
            json.decodeFromString<Map<String, String>>(raw)
        }.getOrNull() ?: return ""
        return payload[SETTINGS_TOKEN_KEY].orEmpty()
            .trim()
            .removePrefix("Bearer ")
            .removePrefix("bearer ")
            .trim()
            .ifBlank { DEFAULT_BEARER_TOKEN }
    }

    fun hasToken(): Boolean = configuredToken().isNotBlank()

    fun saveToken(token: String) {
        val normalized = token.trim().removePrefix("Bearer ").removePrefix("bearer ").trim()
        PluginStorage.saveScraperSettings(
            SETTINGS_SCRAPER_ID,
            json.encodeToString(
                kotlinx.serialization.serializer<Map<String, String>>(),
                mapOf(SETTINGS_TOKEN_KEY to normalized),
            ),
        )
        clearFeedCache()
    }

    // ---------- transport ----------

    private val http: HttpClient by lazy { createApiHttpClient() }

    private fun currentTimeMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

    internal fun apiHeaders(token: String, kind: String): Map<String, String> {
        val headers = linkedMapOf(
            "User-Agent" to if (kind == "stream") "tomato-android" else "okhttp/4.11.0",
            "Authorization" to "Bearer $token",
            "Accept" to "application/json, text/plain, */*",
        )
        if (kind == "stream" || kind == "post") headers["Content-Type"] = "application/json"
        headers["request-time"] = currentTimeMillis().toString()
        headers["x-app"] = "1.4.3"
        return headers
    }

    private suspend fun requestJson(
        path: String,
        kind: String,
        body: JsonObject?,
        attempts: Int = maxAttemptsPerRequest,
    ): JsonObject {
        val token = configuredToken()
        if (token.isBlank()) throw TomatoException.TokenMissing()
        var lastError: Throwable? = null
        repeat(attempts) { attempt ->
            val host = if (attempt % 2 == 0) HOST_EDGE else HOST_PROD
            try {
                val response = http.request("$host$path") {
                    method = if (body != null) HttpMethod.Post else HttpMethod.Get
                    apiHeaders(token, kind).forEach { (key, value) -> header(key, value) }
                    if (body != null) setBody(body.toString())
                }
                if (response.status.value in 200..299) {
                    return json.parseToJsonElement(response.bodyAsText()).jsonObject
                }
                lastError = TomatoException.RequestFailed("Tomato HTTP ${response.status.value} for $path")
            } catch (error: TomatoException.TokenMissing) {
                throw error
            } catch (error: Throwable) {
                lastError = error
            }
            kotlinx.coroutines.delay(retryDelayMillis * (attempt + 1))
        }
        throw (lastError ?: TomatoException.RequestFailed("Tomato request failed for $path"))
    }

    private fun postBody(vararg pairs: Pair<String, JsonPrimitive>): JsonObject =
        JsonObject(playedOutBody(pairs.toList()))

    private fun playedOutBody(pairs: List<Pair<String, JsonPrimitive>>): Map<String, JsonPrimitive> =
        pairs.toMap() + mapOf("token" to JsonPrimitive(configuredToken()))

    // ---------- payload helpers ----------

    internal fun payloadData(payload: JsonObject): JsonObject {
        val data = payload["data"]
        return data as? JsonObject ?: payload
    }

    internal fun arrayField(payload: JsonObject, key: String): List<JsonObject> {
        val value = payload[key] ?: return emptyList()
        return runCatching { value.jsonArray.mapNotNull { it as? JsonObject } }.getOrDefault(emptyList())
    }

    internal fun detailObject(payload: JsonObject): JsonObject =
        (payload["anime_details"] as? JsonObject) ?: payloadData(payload)

    internal fun detailName(payload: JsonObject): String =
        displayTitleOrNull(
            (detailObject(payload)["anime_name"]
                ?: detailObject(payload)["name"]
                ?: detailObject(payload)["title"])
                ?.jsonPrimitive?.contentOrNull.orEmpty(),
        ).orEmpty()

    internal fun isTechnicalTitle(value: String): Boolean {
        val normalized = value.trim()
        return normalized.isBlank() ||
            normalized.matches(Regex("tomato:\\d+(?::\\d+)?", RegexOption.IGNORE_CASE)) ||
            normalized.matches(Regex("tomato\\s+#\\d+", RegexOption.IGNORE_CASE))
    }

    internal fun displayTitleOrNull(value: String): String? =
        value.trim().takeUnless(::isTechnicalTitle)

    internal fun seasonFromJson(payload: JsonObject, index: Int): TomatoSeason? {
        val seasonId = (payload["season_id"] ?: payload["id"])
            ?.jsonPrimitive?.intOrNull?.toLong()?.takeIf { it > 0 } ?: return null
        val label = (payload["season_name"] ?: payload["name"])
            ?.jsonPrimitive?.contentOrNull.orEmpty()
        val explicitNumber = payload["season_number"]?.jsonPrimitive?.intOrNull
        val number = explicitNumber?.takeIf { it > 0 }
            ?: Regex("(?:season|temporada)\\s*(\\d+)", RegexOption.IGNORE_CASE)
                .find(label)?.groupValues?.get(1)?.toIntOrNull()
            ?: (index + 1)
        val dubbed = payload["season_dubbed"]?.jsonPrimitive?.intOrNull == 1 ||
            payload["season_dubbed"]?.jsonPrimitive?.booleanOrNull == true
        return TomatoSeason(
            seasonId = seasonId,
            number = number,
            name = displayTitleOrNull(label),
            dubbed = dubbed,
        )
    }

    internal fun searchItems(payload: JsonObject): List<JsonObject> {
        val data = payload["data"]
        val candidates = buildList {
            add(data)
            (data as? JsonObject)?.let {
                add(it["result"])
                add(it["data"])
            }
        }
        candidates.forEach { value ->
            val items = runCatching { value?.jsonArray?.mapNotNull { it as? JsonObject }.orEmpty() }.getOrNull()
            if (!items.isNullOrEmpty()) return items
        }
        return emptyList()
    }

    internal fun feedItems(value: kotlinx.serialization.json.JsonElement?, output: MutableList<JsonObject>): List<JsonObject> {
        when (value) {
            is kotlinx.serialization.json.JsonArray -> value.forEach { feedItems(it, output) }
            is JsonObject -> {
                if (animeIdOf(value) != null) {
                    output.add(value)
                }
                value.forEach { (_, nested) -> feedItems(nested, output) }
            }
            else -> Unit
        }
        return output
    }

    internal fun animeIdOf(item: JsonObject): Long? {
        val raw = (
            item["anime_id"]
                ?: item["ep_anime_id"]
                ?: item["animeId"]
                ?: item["id"]
            )?.jsonPrimitive?.intOrNull ?: return null
        return raw.takeIf { it > 0 }?.toLong()
    }

    internal fun titleOf(item: JsonObject): String =
        (item["anime_name"] ?: item["name"] ?: item["title"])?.jsonPrimitive?.contentOrNull.orEmpty()

    internal fun imageOf(item: JsonObject): String? =
        (item["image"] ?: item["cape"] ?: item["thumbnail"] ?: item["banner"] ?: item["poster"])
            ?.jsonPrimitive?.contentOrNull

    internal fun episodeNameOf(item: JsonObject): String? =
        (item["ep_name"] ?: item["episode_name"] ?: item["episodeName"] ?: item["episode_title"] ?: item["episodeTitle"])
            ?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }

    private fun explicitEpisodeThumbnailOf(item: JsonObject): String? =
        (item["ep_thumbnail"] ?: item["episode_thumbnail"] ?: item["episodeThumbnail"])
            ?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }

    internal fun episodeThumbnailOf(item: JsonObject): String? =
        explicitEpisodeThumbnailOf(item)
            ?: imageOf(item)?.takeIf { episodeIdOf(item) != null }

    internal fun titleFromFeedItem(item: JsonObject, fallbackName: String? = null): TomatoTitle? {
        val id = animeIdOf(item) ?: return null
        val name = displayTitleOrNull(titleOf(item))
            ?: displayTitleOrNull(fallbackName.orEmpty())
            ?: return null
        val episodeId = feedEpisodeIdOf(item)
        val episodeThumbnail = explicitEpisodeThumbnailOf(item)
            ?: episodeId?.let { imageOf(item) }
        return TomatoTitle(
            id = id,
            name = name,
            image = episodeThumbnail ?: imageOf(item),
            episodeId = episodeId,
            episodeNumber = episodeNumberOf(item),
            episodeName = episodeNameOf(item),
            episodeThumbnail = episodeThumbnail,
        )
    }

    fun videoId(animeId: Long, episodeId: Long): String = "${ANIME_ID_PREFIX}$animeId:$episodeId"

    fun parseAnimeId(metaId: String): Long? =
        metaId.takeIf { it.startsWith(ANIME_ID_PREFIX) }
            ?.removePrefix(ANIME_ID_PREFIX)
            ?.substringBefore(':')
            ?.toLongOrNull()
            ?.takeIf { it > 0 }

    fun parseEpisodeId(videoId: String): Long? =
        videoId.takeIf { it.startsWith(ANIME_ID_PREFIX) }
            ?.removePrefix(ANIME_ID_PREFIX)
            ?.split(':')
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?.takeIf { it > 0 }

    // ---------- catalog: feed + search ----------

    internal fun detailLookupIds(rawSections: List<JsonObject>): List<Long> =
        rawSections.flatMap { section ->
            val key = sectionKey(section["title"]?.jsonPrimitive?.contentOrNull.orEmpty())
            val items = runCatching { section["data"]?.jsonArray.orEmpty() }
                .getOrDefault(emptyList())
            feedItemsForSection(key, items.mapNotNull { item -> item as? JsonObject })
                .take(FEED_ENRICH_LIMIT)
                .filter { item -> displayTitleOrNull(titleOf(item)) == null }
                .mapNotNull(::animeIdOf)
                .distinct()
        }.distinct()

    internal suspend fun feedSections(): List<FeedSectionPayload> = feedCacheMutex.withLock {
        feedCache?.takeIf { currentTimeMillis() < feedCacheExpiresAtMillis }
            ?.let { return@withLock it }
        val payload = requestJson("/v2/animes/feed", kind = "metadata", body = null, attempts = 6)
        val rawSections = (payload["data"] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.filter { it["title"]?.jsonPrimitive?.contentOrNull.orEmpty().isNotBlank() }
            ?: emptyList()
        // Resolve missing names per section so later categories are not starved by
        // an arbitrary global lookup cap.
        val ids = detailLookupIds(rawSections)
        val namesById = buildMap<Long, String> {
            ids.chunked(4).forEach { batch ->
                val resolved = coroutineScope {
                    batch.map { id ->
                        async { id to runCatching { animeDetails(id).name }.getOrNull().orEmpty() }
                    }.awaitAll()
                }
                resolved.forEach { (id, name) -> put(id, name) }
            }
        }
        val sections = rawSections.mapNotNull { section ->
            val title = section["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            val key = sectionKey(title)
            val sourceItems = feedItemsForSection(
                key,
                runCatching { section["data"]?.jsonArray.orEmpty().mapNotNull { it as? JsonObject } }
                    .getOrDefault(emptyList()),
            )
            val items = sourceItems
                .mapNotNull { item ->
                    val id = animeIdOf(item) ?: return@mapNotNull null
                    titleFromFeedItem(item, fallbackName = namesById[id])
                }
            log.d { "category=$key source=${sourceItems.size} resolved=${items.size}" }
            FeedSectionPayload(key, title, items, sourceItems.size)
        }.filter { it.key.isNotBlank() }
            .groupBy(FeedSectionPayload::key)
            .map { (_, matching) ->
                val first = matching.first()
                first.copy(
                    items = matching.flatMap(FeedSectionPayload::items)
                        .distinctBy { title ->
                            if (isLatestEpisodeSection(first.key)) title.episodeId ?: title.id else title.id
                        },
                    sourceItemCount = matching.sumOf(FeedSectionPayload::sourceItemCount),
                )
            }
        feedCacheExpiresAtMillis = if (ids.all { !namesById[it].isNullOrBlank() }) {
            Long.MAX_VALUE
        } else {
            currentTimeMillis() + 60_000L
        }
        return@withLock sections.also { feedCache = it }
    }

    suspend fun feedAnimes(): List<TomatoTitle> =
        feedSections().flatMap { it.items }.distinctBy { it.id }

    fun clearFeedCache() {
        feedCache = null
        feedCacheExpiresAtMillis = 0L
    }

    internal fun cachedFeedSections(): List<FeedSectionPayload> = feedCache.orEmpty()

    internal fun isLatestEpisodeSection(sectionKey: String): Boolean =
        sectionKey in LATEST_EPISODE_SECTION_KEYS

    internal fun feedItemsForSection(sectionKey: String, items: List<JsonObject>): List<JsonObject> {
        if (!isLatestEpisodeSection(sectionKey)) return items.distinctBy(::animeIdOf)
        val seenEpisodeIds = mutableSetOf<Long>()
        val seenAnimeIdsWithoutEpisode = mutableSetOf<Long>()
        return items.filter { item ->
            val episodeId = feedEpisodeIdOf(item)
            if (episodeId != null) {
                seenEpisodeIds.add(episodeId)
            } else {
                val animeId = animeIdOf(item)
                animeId == null || seenAnimeIdsWithoutEpisode.add(animeId)
            }
        }
    }

    private fun feedEpisodeIdOf(item: JsonObject): Long? {
        val raw = item["ep_id"]
            ?: item["episode_id"]
            ?: item["episodeId"]
            ?: item["id"]?.takeIf {
                "anime_id" in item || "ep_anime_id" in item || "animeId" in item
            }
        return raw?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }?.toLong()
    }

    internal fun sectionKey(title: String): String {
        val normalized = title.lowercase()
            .replace('á', 'a').replace('ã', 'a').replace('â', 'a')
            .replace('é', 'e').replace('ê', 'e')
            .replace('í', 'i').replace('ó', 'o').replace('ô', 'o')
            .replace('ú', 'u').replace('ç', 'c')
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return when (normalized) {
            "animes-em-alta" -> "em-alta"
            else -> normalized
        }
    }

    suspend fun searchAnimes(query: String): List<TomatoTitle> {
        val payload = requestJson(
            path = "/v2/content/search",
            kind = "post",
            body = postBody(
                "search" to JsonPrimitive(query),
                "content_type" to JsonPrimitive("all"),
                "page" to JsonPrimitive(0),
            ),
        )
        return searchItems(payload).mapNotNull { item ->
            val id = animeIdOf(item) ?: return@mapNotNull null
            val name = displayTitleOrNull(titleOf(item)) ?: return@mapNotNull null
            TomatoTitle(id = id, name = name, image = imageOf(item))
        }
    }

    // ---------- details ----------

    suspend fun animeDetails(animeId: Long): TomatoDetails {
        val payload = requestJson("/v2/anime/$animeId", kind = "metadata", body = null, attempts = 6)
        val data = payloadData(payload)
        val details = detailObject(payload)
        val seasons = (payload["anime_seasons"] ?: data["anime_seasons"] ?: data["seasons"])
            ?.let { runCatching { it.jsonArray.mapNotNull { entry -> entry as? JsonObject } }.getOrNull() }
            .orEmpty()
        return TomatoDetails(
            animeId = animeId,
            name = detailName(payload),
            description = (details["anime_description"] ?: details["description"] ?: details["sinopse"])
                ?.jsonPrimitive?.contentOrNull,
            poster = (details["anime_cape_url"] ?: details["anime_cover_url"] ?: details["cape"]
                ?: details["thumbnail"] ?: details["poster"] ?: details["image"])
                ?.jsonPrimitive?.contentOrNull,
            banner = (details["anime_banner_url"] ?: details["banner"])?.jsonPrimitive?.contentOrNull,
            tags = buildList {
                val genre = details["anime_genre"]?.jsonPrimitive?.contentOrNull
                if (!genre.isNullOrBlank()) addAll(genre.split(',').map(String::trim).filter(String::isNotBlank))
                listOf("genres", "tags", "categories").forEach { key ->
                    details[key]?.let { value ->
                        addAll(runCatching { value.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull } }
                            .getOrDefault(emptyList()))
                    }
                }
            }.distinct(),
            seasons.mapIndexedNotNull { index, season -> seasonFromJson(season, index) }
                .distinctBy { it.seasonId },
        )
    }

    suspend fun seasonEpisodes(season: TomatoSeason): List<TomatoEpisode> {
        val seenIds = mutableSetOf<Long>()
        val result = mutableListOf<TomatoEpisode>()
        for (page in 0 until 20) {
            val payload = runCatching {
                requestJson(
                    path = "/season/${season.seasonId}/episodes",
                    kind = "post",
                    body = postBody(
                        "page" to JsonPrimitive(page),
                        "order" to JsonPrimitive("ASC"),
                    ),
                    attempts = if (page == 0) 3 else 2,
                )
            }.getOrNull() ?: break
            val items = episodeItems(payload)
            if (items.isEmpty()) break
            items.forEach { item ->
                val episode = episodeFromJson(item) ?: return@forEach
                if (seenIds.add(episode.episodeId)) result.add(episode)
            }
            val declaredTotal = episodeTotalOf(payload)
            if (declaredTotal != null && seenIds.size >= declaredTotal) break
        }
        return result
    }

    internal fun episodeItems(payload: JsonObject): List<JsonObject> {
        val data = payload["data"]
        (data as? kotlinx.serialization.json.JsonArray)?.let { array ->
            return array.mapNotNull { it as? JsonObject }
        }
        val episodes = (data as? JsonObject)?.get("episodes")
        return episodes?.let { runCatching { it.jsonArray.mapNotNull { entry -> entry as? JsonObject } }.getOrNull() }
            .orEmpty()
    }

    internal fun episodeFromJson(item: JsonObject): TomatoEpisode? {
        val id = episodeIdOf(item) ?: return null
        return TomatoEpisode(
            episodeId = id,
            number = episodeNumberOf(item),
            name = episodeNameOf(item),
            thumbnail = episodeThumbnailOf(item),
            dubbed = item["dubbed"]?.jsonPrimitive?.booleanOrNull == true,
        )
    }

    internal fun episodeTotalOf(payload: JsonObject): Int? =
        runCatching {
            (payload["episodes"] ?: (payload["data"] as? JsonObject)?.get("episodes"))
                ?.jsonPrimitive?.intOrNull
        }.getOrNull()

    internal fun episodeIdOf(item: JsonObject): Long? =
        (item["ep_id"] ?: item["episode_id"] ?: item["episodeId"] ?: item["id"])
            ?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }?.toLong()

    internal fun episodeNumberOf(item: JsonObject): Int? {
        val explicit = (item["episode_number"] ?: item["ep_number"] ?: item["episodeNumber"] ?: item["number"])
            ?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }
        if (explicit != null) return explicit
        return episodeNameOf(item)?.let { name ->
            Regex("^\\s*(\\d+)\\s*(?:[.)\\-:]|$)").find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
    }

    // ---------- streams ----------

    internal val qualityFields = listOf(
        "fhd" to "1080p",
        "mhd" to "720p",
        "hd" to "HD",
        "shd" to "480p",
        "sd" to "360p",
    )

    suspend fun episodeStreams(animeId: Long, episodeId: Long): List<StreamItem> {
        val payload = requestJson(
            path = "/v2/anime/episode/$episodeId/stream",
            kind = "stream",
            body = null,
            attempts = 4,
        )
        val streams = payloadData(payload)["streams"] as? JsonObject ?: return emptyList()
        return qualityFields.mapNotNull { (field, quality) ->
            val url = streams[field]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?: return@mapNotNull null
            StreamItem(
                name = "$PROVIDER_NAME • $quality",
                title = "$PROVIDER_NAME • $quality",
                url = url,
                addonName = PROVIDER_NAME,
                addonId = "tomato",
                streamType = "hls",
                behaviorHints = StreamBehaviorHints(
                    notWebReady = true,
                    proxyHeaders = StreamProxyHeaders(
                        request = mapOf("User-Agent" to "tomato-android"),
                    ),
                ),
            )
        }.distinctBy(StreamItem::url)
    }
}

data class TomatoTitle(
    val id: Long,
    val name: String,
    val image: String?,
    val episodeId: Long? = null,
    val episodeNumber: Int? = null,
    val episodeName: String? = null,
    val episodeThumbnail: String? = null,
)

data class TomatoSeason(
    val seasonId: Long,
    val number: Int,
    val name: String?,
    val dubbed: Boolean,
)

data class TomatoEpisode(
    val episodeId: Long,
    val number: Int?,
    val name: String?,
    val thumbnail: String?,
    val dubbed: Boolean,
)

data class TomatoDetails(
    val animeId: Long,
    val name: String,
    val description: String?,
    val poster: String?,
    val banner: String?,
    val tags: List<String>,
    val seasons: List<TomatoSeason>,
)
