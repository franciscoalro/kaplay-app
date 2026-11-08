package com.kaplay.app.features.tomato

import com.kaplay.app.features.catalog.CatalogPage
import com.kaplay.app.features.catalog.CatalogTarget
import com.kaplay.app.features.home.HomeCatalogDefinition
import com.kaplay.app.features.home.MetaPreview
import com.kaplay.app.features.streams.StreamItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Enriches every feed card after the base payload has been published. The
 * callback receives a complete, order-preserving snapshot after each success;
 * a failed card simply remains in its base form.
 */
internal class TomatoProgressiveEnricher(
    private val maxConcurrency: Int = 4,
    private val loadDetails: suspend (Long) -> TomatoDetails?,
) {
    suspend fun enrich(
        sections: List<TomatoProvider.FeedSectionPayload>,
        onUpdate: suspend (List<TomatoProvider.FeedSectionPayload>) -> Unit,
    ) = supervisorScope {
        val semaphore = Semaphore(maxConcurrency.coerceAtLeast(1))
        val updatesMutex = Mutex()
        var enrichedSections = sections
        val ids = sections.flatMap { section -> section.items.map(TomatoTitle::id) }.distinct()
        val jobs = ids.map { id ->
            launch {
                val details = try {
                    semaphore.withPermit { loadDetails(id) }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    null
                } ?: return@launch
                updatesMutex.withLock {
                    enrichedSections = enrichedSections.map { section ->
                        section.copy(items = section.items.map { item ->
                            if (item.id == id) item.withDetails(details) else item
                        })
                    }
                    onUpdate(enrichedSections)
                }
            }
        }
        jobs.joinAll()
    }

    private fun TomatoTitle.withDetails(details: TomatoDetails): TomatoTitle {
        val resolvedName = TomatoProvider.displayTitleOrNull(details.name) ?: name
        val resolvedImage = if (episodeId != null && episodeThumbnail != null) {
            image
        } else {
            details.poster ?: image
        }
        return copy(name = resolvedName, image = resolvedImage)
    }
}

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

    internal fun titlesForSection(
        sectionId: String?,
        sections: List<TomatoProvider.FeedSectionPayload>,
        globalItems: List<TomatoTitle>,
    ): List<TomatoTitle> {
        val normalizedSectionId = sectionId?.removePrefix("tomato:")
        return if (normalizedSectionId == null) {
            globalItems
        } else {
            sections.firstOrNull { section -> section.key == normalizedSectionId }?.items.orEmpty()
        }
    }

    suspend fun feedPage(sectionId: String? = null): CatalogPage {
        val sections = TomatoProvider.feedSections()
        return feedPageFromSections(sectionId, sections)
    }

    internal fun feedPageFromSections(
        sectionId: String?,
        sections: List<TomatoProvider.FeedSectionPayload>,
    ): CatalogPage {
        val section = sectionId?.let { id ->
            val normalizedId = id.removePrefix("tomato:")
            sections.firstOrNull { it.key == normalizedId }
        }
        val globalItems = if (sectionId == null) {
            sections.flatMap { it.items }.distinctBy(TomatoTitle::id)
        } else {
            emptyList()
        }
        val titles = titlesForSection(sectionId, sections, globalItems)
        val latestEpisode = section?.key?.let(TomatoProvider::isLatestEpisodeSection) == true
        val sourceCount = section?.sourceItemCount ?: globalItems.size
        return CatalogPage(
            items = titles.map { title -> metaPreview(title, latestEpisode = latestEpisode) },
            rawItemCount = sourceCount.coerceAtLeast(titles.size),
            nextSkip = null,
        )
    }

    internal suspend fun enrichFeedSections(
        sections: List<TomatoProvider.FeedSectionPayload>,
        onUpdate: suspend (List<TomatoProvider.FeedSectionPayload>) -> Unit,
    ) {
        TomatoProgressiveEnricher(loadDetails = TomatoProvider::cachedFeedDetails)
            .enrich(sections, onUpdate)
    }

    suspend fun searchPage(query: String): CatalogPage {
        return searchPageFromTitles(TomatoProvider.searchAnimes(query))
    }

    internal fun searchPageFromTitles(titles: List<TomatoTitle>): CatalogPage {
        return CatalogPage(
            items = titles.map { title -> metaPreview(title) },
            rawItemCount = titles.size,
            nextSkip = null,
        )
    }

    suspend fun page(target: CatalogTarget.Tomato): CatalogPage =
        if (target.query.isNullOrBlank()) feedPage(target.sectionId) else searchPage(target.query)
}
