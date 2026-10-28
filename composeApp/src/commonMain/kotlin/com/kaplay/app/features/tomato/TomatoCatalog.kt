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

    fun metaPreview(title: TomatoTitle): MetaPreview = MetaPreview(
        id = "${TomatoProvider.ANIME_ID_PREFIX}${title.id}",
        type = "tv",
        name = title.name,
        poster = title.image,
        banner = title.image,
    )

    suspend fun feedPage(sectionId: String? = null): CatalogPage {
        val section = sectionId?.let { id ->
            TomatoProvider.feedSections().firstOrNull { it.key == id }
        }
        val titles = (section?.items ?: TomatoProvider.feedAnimes()).take(FEED_ROW_LIMIT)
        return CatalogPage(
            items = titles.map(::metaPreview),
            rawItemCount = titles.size,
            nextSkip = null,
        )
    }

    suspend fun searchPage(query: String): CatalogPage {
        val titles = TomatoProvider.searchAnimes(query).take(FEED_ROW_LIMIT)
        return CatalogPage(
            items = titles.map(::metaPreview),
            rawItemCount = titles.size,
            nextSkip = null,
        )
    }

    suspend fun page(target: CatalogTarget.Tomato): CatalogPage =
        if (target.query.isNullOrBlank()) feedPage() else searchPage(target.query)
}
