package com.kaplay.app.features.catalog

import com.kaplay.app.core.poster.CustomPosterUrlRepository
import com.kaplay.app.core.poster.withCustomPosterUrls
import com.kaplay.app.features.collection.CollectionRepository
import com.kaplay.app.features.collection.TmdbCollectionSourceResolver
import com.kaplay.app.features.collection.catalogRouteKey
import com.kaplay.app.features.library.LibraryRepository
import com.kaplay.app.features.servers.ServerCatalog
import com.kaplay.app.features.servers.serverMessage
import com.kaplay.app.features.home.HomeCatalogSettingsRepository
import com.kaplay.app.features.home.MetaPreview
import com.kaplay.app.features.home.filterReleasedItems
import com.kaplay.app.features.home.stableKey
import com.kaplay.app.features.tomato.TomatoCatalog
import com.kaplay.app.features.trakt.TraktPublicListSourceResolver
import com.kaplay.app.features.watchprogress.CurrentDateProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kaplay.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString

object CatalogRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _uiState = MutableStateFlow(CatalogUiState())
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null
    private var activeRequest: CatalogRequest? = null
    private var requestGeneration = 0L
    private val scrollPositions = linkedMapOf<CatalogRequest, CatalogScrollPosition>()

    fun load(
        target: CatalogTarget,
        force: Boolean = false,
    ) {
        val request = catalogRequest(target)
        if (!force && activeRequest == request && (_uiState.value.items.isNotEmpty() || _uiState.value.isLoading)) {
            return
        }
        activeRequest = request
        if (target is CatalogTarget.Library) {
            observeInternalLibrary(request)
            return
        }
        fetchPage(
            request = request,
            reset = true,
            forceRefresh = force,
        )
    }

    fun loadMore() {
        val request = activeRequest ?: return
        val current = _uiState.value
        if (current.isLoading || current.nextSkip == null) return
        fetchPage(
            request = request,
            reset = false,
            forceRefresh = false,
        )
    }

    fun clear() {
        requestGeneration += 1
        activeJob?.cancel()
        activeRequest = null
        scrollPositions.clear()
        _uiState.value = CatalogUiState()
    }

    fun scrollPosition(
        target: CatalogTarget,
    ): CatalogScrollPosition =
        scrollPositions[catalogRequest(target)]
            ?: CatalogScrollPosition()

    fun saveScrollPosition(
        target: CatalogTarget,
        firstVisibleItemIndex: Int,
        firstVisibleItemScrollOffset: Int,
    ) {
        val request = catalogRequest(target)
        scrollPositions[request] = CatalogScrollPosition(
            firstVisibleItemIndex = firstVisibleItemIndex,
            firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
        )
    }

    private fun observeInternalLibrary(request: CatalogRequest) {
        activeJob?.cancel()
        val generation = ++requestGeneration
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            errorMessage = null,
        )

        activeJob = scope.launch {
            try {
                val target = request.target as CatalogTarget.Library
                LibraryRepository.ensureLoaded()
                LibraryRepository.uiState.libraryCatalogStates(
                    target, LibraryRepository.uiState.libraryCatalogOrders(target),
                ).collect { state ->
                    if (!isCurrent(request, generation)) return@collect
                    CustomPosterUrlRepository.ensureLoaded()
                    val posterPattern = CustomPosterUrlRepository.patternForScreen(
                        com.kaplay.app.core.poster.CustomPosterScreen.LIBRARY
                    )
                    _uiState.value = state.copy(
                        items = state.items.withCustomPosterUrls(posterPattern)
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!isCurrent(request, generation)) return@launch
                _uiState.value = CatalogUiState(
                    errorMessage = error.serverMessage() ?: error.message ?: getString(Res.string.catalog_load_failed),
                )
            }
        }
    }

    private fun fetchPage(
        request: CatalogRequest,
        reset: Boolean,
        forceRefresh: Boolean,
    ) {
        activeJob?.cancel()
        val generation = ++requestGeneration
        val current = _uiState.value
        val requestedSkip = if (reset) 0 else current.nextSkip ?: return

        _uiState.value = current.copy(
            items = if (reset) emptyList() else current.items,
            isLoading = true,
            nextSkip = if (reset) null else current.nextSkip,
            errorMessage = null,
        )

        activeJob = scope.launch {
            runCatchingCancellable {
                when (val target = request.target) {
                    is CatalogTarget.Addon -> fetchCatalogPage(
                        manifestUrl = target.manifestUrl,
                        type = target.contentType,
                        catalogId = target.catalogId,
                        genre = target.genre,
                        skip = requestedSkip.takeIf { it > 0 },
                        forceRefresh = forceRefresh,
                    )

                    is CatalogTarget.CollectionSource -> fetchCollectionSourcePage(
                        target = target,
                        page = requestedSkip.takeIf { it > 0 } ?: 1,
                    )

                    is CatalogTarget.Tomato -> TomatoCatalog.page(target)
                    is CatalogTarget.Server -> ServerCatalog.page(target, skip = requestedSkip)

                    is CatalogTarget.Library -> error(getString(Res.string.catalog_load_failed))
                }.withUnreleasedFilter(request.hideUnreleasedContent)
            }.fold(
                onSuccess = { page ->
                    if (!isCurrent(request, generation)) return@fold

                    val mergedItems = if (reset) {
                        dedupeCatalogItems(page.items)
                    } else {
                        mergeCatalogItems(_uiState.value.items, page.items)
                    }
                    val supportsPagination = request.target.supportsPagination || page.rawItemCount >= CATALOG_PAGE_SIZE
                    val loadedNewItems = reset || mergedItems.size > current.items.size
                    val paginationState = nextCatalogPaginationState(
                        supportsPagination = supportsPagination,
                        requestedSkip = requestedSkip,
                        page = page,
                        loadedNewItems = loadedNewItems,
                        consecutiveDuplicatePages = if (reset) 0 else current.consecutiveDuplicatePages,
                    )
                    CustomPosterUrlRepository.ensureLoaded()
                    val posterScreen = when (request.target) {
                        is CatalogTarget.Library -> com.kaplay.app.core.poster.CustomPosterScreen.LIBRARY
                        is CatalogTarget.CollectionSource -> com.kaplay.app.core.poster.CustomPosterScreen.COLLECTIONS
                        else -> com.kaplay.app.core.poster.CustomPosterScreen.HOME
                    }
                    val posterPattern = CustomPosterUrlRepository.patternForScreen(posterScreen)
                    _uiState.value = CatalogUiState(
                        items = mergedItems.withCustomPosterUrls(posterPattern),
                        isLoading = false,
                        nextSkip = paginationState.nextSkip,
                        consecutiveDuplicatePages = paginationState.consecutiveDuplicatePages,
                        errorMessage = null,
                    )

                    val tomatoTarget = request.target as? CatalogTarget.Tomato
                    if (tomatoTarget != null && tomatoTarget.query.isNullOrBlank()) {
                        TomatoCatalog.enrichPage(
                            target = tomatoTarget,
                        ) { enrichedPage ->
                            if (isCurrent(request, generation)) {
                                val enrichedItems = mergeTomatoEnrichment(
                                    existing = _uiState.value.items,
                                    incoming = enrichedPage
                                        .withUnreleasedFilter(request.hideUnreleasedContent)
                                        .items,
                                )
                                _uiState.value = _uiState.value.copy(
                                    items = enrichedItems.withCustomPosterUrls(posterPattern),
                                )
                            }
                        }
                    }
                },
                onFailure = { error ->
                    if (!isCurrent(request, generation)) return@fold

                    _uiState.value = current.copy(
                        items = if (reset) emptyList() else current.items,
                        isLoading = false,
                        nextSkip = nextCatalogSkipAfterFailure(reset, current.nextSkip),
                        errorMessage = error.serverMessage() ?: error.message ?: getString(Res.string.catalog_load_failed),
                    )
                },
            )
        }
    }

    private fun catalogRequest(target: CatalogTarget): CatalogRequest =
        CatalogRequest(
            target = target,
            hideUnreleasedContent = HomeCatalogSettingsRepository.snapshot().hideUnreleasedContent,
        )

    private fun isCurrent(request: CatalogRequest, generation: Long): Boolean =
        activeRequest == request && requestGeneration == generation
}

private suspend inline fun <T> runCatchingCancellable(
    crossinline block: suspend () -> T,
): Result<T> = try {
    Result.success(block())
} catch (error: CancellationException) {
    throw error
} catch (error: Throwable) {
    Result.failure(error)
}

internal fun shouldAutoLoadMore(
    canLoadMore: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
): Boolean = canLoadMore && !isLoading && errorMessage.isNullOrBlank()

private fun CatalogPage.withUnreleasedFilter(hideUnreleasedContent: Boolean): CatalogPage {
    if (!hideUnreleasedContent) return this
    val filteredItems = items.filterReleasedItems(CurrentDateProvider.todayIsoDate())
    return if (filteredItems.size == items.size) this else copy(items = filteredItems)
}

/**
 * Applies a Tomato detail snapshot to the cards already shown by the grid.
 * The base order is authoritative and stable keys are retained, so a late
 * detail response can update metadata without removing, moving, or replacing
 * a card identity. Any card unexpectedly absent from the snapshot is kept.
 */
private fun mergeTomatoEnrichment(
    existing: List<MetaPreview>,
    incoming: List<MetaPreview>,
): List<MetaPreview> {
    if (existing.isEmpty()) return dedupeCatalogItems(incoming)
    val incomingByKey = dedupeCatalogItems(incoming).associateBy { it.stableKey() }
    val existingKeys = existing.mapTo(mutableSetOf()) { it.stableKey() }
    return buildList(existing.size + incoming.size) {
        existing.forEach { item ->
            add(incomingByKey[item.stableKey()] ?: item)
        }
        incoming.forEach { item ->
            if (item.stableKey() !in existingKeys) add(item)
        }
    }
}

private suspend fun fetchCollectionSourcePage(
    target: CatalogTarget.CollectionSource,
    page: Int,
): CatalogPage {
    CollectionRepository.initialize()
    val source = CollectionRepository.getCollection(target.collectionId)
        ?.folders
        ?.firstOrNull { it.id == target.folderId }
        ?.resolvedSources
        ?.firstOrNull { it.catalogRouteKey() == target.sourceKey }
        ?: error(getString(Res.string.catalog_load_failed))

    return when {
        source.isTmdb -> TmdbCollectionSourceResolver.resolve(source = source, page = page)
        source.isTrakt -> TraktPublicListSourceResolver.resolve(source = source, page = page)
        else -> error(getString(Res.string.catalog_load_failed))
    }
}

private data class CatalogRequest(
    val target: CatalogTarget,
    val hideUnreleasedContent: Boolean,
)
