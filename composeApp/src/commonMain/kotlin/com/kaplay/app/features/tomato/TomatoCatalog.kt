package com.kaplay.app.features.tomato

import com.kaplay.app.features.catalog.CatalogPage
import com.kaplay.app.features.catalog.CatalogTarget
import com.kaplay.app.features.home.HomeCatalogDefinition
import com.kaplay.app.features.home.MetaPreview
import com.kaplay.app.features.streams.StreamItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Builtin Tomato catalog surfaces (home row, catalog grid, search) built on
 * top of [TomatoProvider]. Keeps all Tomato identity in one module.
 */
object TomatoCatalog {
    internal const val MANIFEST_URL = "builtin:tomato"
    internal const val CATALOG_ID = "tomato-feed"

    private val HOME_SECTIONS = listOf(
        "em-alta" to "Animes em alta",
        "novos-episodios" to "Novos episódios",
        "recem-adicionados" to "Recém adicionados",
        "semanais" to "Semanais",
        "com-dublagem" to "Com dublagem",
        "aventura" to "Aventura",
        "comedia" to "Comédia",
        "romance" to "Romance",
        "slice-of-life" to "Slice Of Life",
    )

    private const val FEED_ROW_LIMIT = 20

    fun homeDefinitions(): List<HomeCatalogDefinition> = HOME_SECTIONS.map { (sectionId, title) ->
        HomeCatalogDefinition(
            key = "tomato:$sectionId",
            defaultTitle = title,
            catalogName = title,
            addonName = TomatoProvider.PROVIDER_NAME,
            manifestUrl = MANIFEST_URL,
            type = "tv",
            catalogId = sectionId,
            supportsPagination = false,
            descriptorSignature = "tomato-feed-v2:$sectionId",
        )
    }

    internal fun latestEpisodeLabel(title: TomatoTitle): String {
        val animeName = title.name.trim()
        val episodeNumber = title.episodeNumber?.takeIf { it > 0 }?.let { "Episódio $it" }
        val episodeName = title.episodeName
            ?.trim()
            ?.replace(Regex("^\\d+\\s*[.)\\-:]\\s*"), "")
            ?.takeIf { it.isNotBlank() }
        val episodeDetails = when {
            episodeNumber != null && episodeName != null -> "$episodeNumber: $episodeName"
            episodeNumber != null -> episodeNumber
            else -> episodeName
        }
        return if (episodeDetails == null) animeName else "$animeName — $episodeDetails"
    }

    fun metaPreview(title: TomatoTitle, latestEpisode: Boolean = false): MetaPreview {
        val id = "${TomatoProvider.ANIME_ID_PREFIX}${title.id}"
        val episodeIdentity = if (latestEpisode) {
            title.episodeId?.takeIf { it > 0 }?.let { "$id:episode:$it" }
        } else {
            null
        }
        val image = if (latestEpisode) title.episodeThumbnail ?: title.image else title.image
        return MetaPreview(
            id = id,
            type = "tv",
            name = if (latestEpisode) latestEpisodeLabel(title) else title.name,
            poster = image,
            banner = image,
            identityKey = episodeIdentity,
        )
    }

    suspend fun feedPage(sectionId: String? = null): CatalogPage {
        val section = sectionId?.let { id ->
            TomatoProvider.feedSections().firstOrNull { it.key == id }
        }
        val titles = (section?.items ?: TomatoProvider.feedAnimes()).take(FEED_ROW_LIMIT)
        val latestEpisode = section?.key?.let(TomatoProvider::isLatestEpisodeSection) == true
        return CatalogPage(
            items = titles.map { title -> metaPreview(title, latestEpisode = latestEpisode) },
            rawItemCount = titles.size,
            nextSkip = null,
        )
    }

    suspend fun searchPage(query: String): CatalogPage {
        val titles = TomatoProvider.searchAnimes(query).take(FEED_ROW_LIMIT)
        return CatalogPage(
            items = titles.map { title -> metaPreview(title) },
            rawItemCount = titles.size,
            nextSkip = null,
        )
    }

    suspend fun page(target: CatalogTarget.Tomato): CatalogPage =
        if (target.query.isNullOrBlank()) feedPage(target.sectionId) else searchPage(target.query)
}
