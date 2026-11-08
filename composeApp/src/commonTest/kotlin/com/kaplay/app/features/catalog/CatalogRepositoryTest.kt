package com.kaplay.app.features.catalog

import com.kaplay.app.features.home.stableKey
import com.kaplay.app.features.tomato.TomatoCatalog
import com.kaplay.app.features.tomato.TomatoDetails
import com.kaplay.app.features.tomato.TomatoProvider
import com.kaplay.app.features.tomato.TomatoTitle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogRepositoryTest {
    @Test
    fun tomatoGridPublishesBaseThenEnrichesWithoutChangingCardOrderOrIdentity() = runBlocking {
        val detailRelease = CompletableDeferred<Unit>()
        TomatoProvider.resetDetailStateForTests()
        TomatoProvider.setDetailLookupForTests {
            detailRelease.await()
            TomatoDetails(
                animeId = it,
                name = "Resolved $it",
                description = null,
                poster = "https://cdn.example/$it-detail.jpg",
                banner = null,
                tags = emptyList(),
                seasons = emptyList(),
            )
        }
        TomatoCatalog.feedSectionsLoaderForTests = {
            listOf(
                section(
                    key = "em-alta",
                    titles = listOf(
                        TomatoTitle(1L, "Base one", "https://cdn.example/1-base.jpg"),
                        TomatoTitle(2L, "Base two", "https://cdn.example/2-base.jpg"),
                    ),
                ),
            )
        }
        CatalogRepository.clear()

        try {
            CatalogRepository.load(CatalogTarget.Tomato(query = null, sectionId = "tomato:em-alta"))
            val base = withTimeout(5_000) {
                CatalogRepository.uiState.first { state ->
                    !state.isLoading && state.items.map { it.name } == listOf("Base one", "Base two")
                }
            }
            val baseOrder = base.items.map { it.id }
            val baseIdentity = base.items.map { it.stableKey() }
            assertEquals(listOf("tomato:1", "tomato:2"), baseOrder)

            detailRelease.complete(Unit)
            val enriched = withTimeout(5_000) {
                CatalogRepository.uiState.first { state ->
                    state.items.map { it.name } == listOf("Resolved 1", "Resolved 2")
                }
            }

            assertEquals(baseOrder, enriched.items.map { it.id })
            assertEquals(baseIdentity, enriched.items.map { it.stableKey() })
            assertEquals(
                listOf("https://cdn.example/1-detail.jpg", "https://cdn.example/2-detail.jpg"),
                enriched.items.map { it.poster },
            )
        } finally {
            detailRelease.complete(Unit)
            CatalogRepository.clear()
            TomatoCatalog.feedSectionsLoaderForTests = null
            TomatoProvider.resetDetailStateForTests()
        }
    }

    @Test
    fun tomatoGridDropsLateEnrichmentWhenTargetChanges() = runBlocking {
        val firstDetailStarted = CompletableDeferred<Unit>()
        val releaseFirstDetail = CompletableDeferred<Unit>()
        var currentTarget = "first"
        TomatoProvider.resetDetailStateForTests()
        TomatoProvider.setDetailLookupForTests { id ->
            if (id == 11L) {
                firstDetailStarted.complete(Unit)
                releaseFirstDetail.await()
                TomatoDetails(11L, "Late first detail", null, "first-detail", null, emptyList(), emptyList())
            } else {
                TomatoDetails(22L, "Second detail", null, "second-detail", null, emptyList(), emptyList())
            }
        }
        TomatoCatalog.feedSectionsLoaderForTests = {
            if (currentTarget == "first") {
                listOf(section("em-alta", listOf(TomatoTitle(11L, "First base", "first-base"))))
            } else {
                listOf(section("em-alta", listOf(TomatoTitle(22L, "Second base", "second-base"))))
            }
        }
        CatalogRepository.clear()

        try {
            val firstTarget = CatalogTarget.Tomato(query = null, sectionId = "tomato:em-alta")
            CatalogRepository.load(firstTarget)
            withTimeout(5_000) {
                CatalogRepository.uiState.first { it.items.map { item -> item.id } == listOf("tomato:11") }
            }
            withTimeout(5_000) { firstDetailStarted.await() }

            currentTarget = "second"
            CatalogRepository.load(CatalogTarget.Tomato(query = null, sectionId = "tomato:em-alta"), force = true)
            val second = withTimeout(5_000) {
                CatalogRepository.uiState.first { state ->
                    state.items.map { it.id } == listOf("tomato:22") && state.items.first().name == "Second detail"
                }
            }
            releaseFirstDetail.complete(Unit)

            withTimeout(5_000) {
                CatalogRepository.uiState.first { state ->
                    state.items.map { it.id } == listOf("tomato:22")
                }
            }
            assertEquals(listOf("tomato:22"), second.items.map { it.id })
        } finally {
            releaseFirstDetail.complete(Unit)
            CatalogRepository.clear()
            TomatoCatalog.feedSectionsLoaderForTests = null
            TomatoProvider.resetDetailStateForTests()
        }
    }

    private fun section(key: String, titles: List<TomatoTitle>) =
        TomatoProvider.FeedSectionPayload(
            key = key,
            title = key,
            items = titles,
            sourceItemCount = titles.size,
        )
}
