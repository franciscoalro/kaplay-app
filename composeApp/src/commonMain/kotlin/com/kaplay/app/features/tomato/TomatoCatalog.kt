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

    private const val FEED_ROW_LIMIT = 20

    fun homeDefinitions(): List<HomeCatalogDefinition> = listOf(
        HomeCatalogDefinition(
            key = "tomato:$CATALOG_ID",
            defaultTitle = "Tomato · Animes em alta",
            catalogName = "Animes em alta",
            addonName = TomatoProvider.PROVIDER_NAME,
            manifestUrl = MANIFEST_URL,
            type = "tv",
            catalogId = CATALOG_ID,
            supportsPagination = false,
            descriptorSignature = "tomato-feed-v1",
        ),
    )

    fun metaPreview(title: TomatoTitle): MetaPreview = MetaPreview(
        id = "${TomatoProvider.ANIME_ID_PREFIX}${title.id}",
        type = "tv",
        name = title.name,
        poster = title.image,
        banner = title.image,
    )

    suspend fun feedPage(): CatalogPage {
        val titles = TomatoProvider.feedAnimes().take(FEED_ROW_LIMIT)
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
