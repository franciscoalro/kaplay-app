package com.kaplay.app.features.watched

import co.touchlab.kermit.Logger
import com.kaplay.app.core.auth.AuthRepository
import com.kaplay.app.core.auth.AuthState
import com.kaplay.app.core.tracking.ensureTrackingProvidersRegistered
import com.kaplay.app.features.details.MetaDetails
import com.kaplay.app.features.details.MetaVideo
import com.kaplay.app.features.profiles.ProfileRepository
import com.kaplay.app.features.servers.ServerWatched
import com.kaplay.app.features.tracking.TrackingProviderId
import com.kaplay.app.features.tracking.TrackingProviderRegistry
import com.kaplay.app.features.tracking.TrackingSettingsRepository
import com.kaplay.app.features.tracking.WatchProgressSource
import com.kaplay.app.features.tracking.effectiveWatchProgressSource
import com.kaplay.app.features.tracking.providerId
import com.kaplay.app.features.watchprogress.ContinueWatchingPreferencesRepository
import com.kaplay.app.features.watching.sync.SupabaseWatchedSyncAdapter
import com.kaplay.app.features.watching.sync.WatchedDeltaEvent
import com.kaplay.app.features.watching.sync.WatchedSyncAdapter
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal enum class WatchedTrackerHistorySync {
    Mirror,
    Skip,
}

internal data class WatchedPushOutcome(
    val kaplaySyncSucceeded: Boolean = false,
    val succeededTrackerProviderIds: Set<TrackingProviderId> = emptySet(),
)

internal fun shouldMirrorWatchedMarkToTrackers(
    sync: WatchedTrackerHistorySync,
    hasConnectedTracker: Boolean,
): Boolean = sync == WatchedTrackerHistorySync.Mirror && hasConnectedTracker

internal data class WatchedSourceOperation(
    val source: WatchProgressSource,
    val generation: Long,
)

internal fun isWatchedSourceOperationCurrent(
    operation: WatchedSourceOperation,
    activeSource: WatchProgressSource,
    activeGeneration: Long,
): Boolean = operation.source == activeSource && operation.generation == activeGeneration

internal fun watchedItemsForSource(
    source: WatchProgressSource,
    kaplayItems: Collection<WatchedItem>,
    providerItems: Map<TrackingProviderId, Collection<WatchedItem>>,
): Collection<WatchedItem> = source.providerId
    ?.let { providerId -> providerItems[providerId].orEmpty() }
    ?: kaplayItems

internal fun shouldAcknowledgeKAPlayWatchedPush(
    source: WatchProgressSource,
    outcome: WatchedPushOutcome,
): Boolean = source.providerId == null && outcome.kaplaySyncSucceeded

internal fun replaceWatchedItemsForSource(
    source: WatchProgressSource,
    kaplayItems: MutableMap<String, WatchedItem>,
    providerItems: MutableMap<TrackingProviderId, MutableMap<String, WatchedItem>>,
    replacement: Map<String, WatchedItem>,
) {
    val target = source.providerId
        ?.let { providerId -> providerItems.getOrPut(providerId, ::mutableMapOf) }
        ?: kaplayItems
    target.clear()
    target.putAll(replacement)
}

internal suspend fun <T> watchedProviderRefreshOrNull(
    refresh: suspend () -> T,
    onFailure: (Throwable) -> Unit,
): T? = try {
    refresh()
} catch (error: CancellationException) {
    throw error
} catch (error: Throwable) {
    onFailure(error)
    null
}

internal fun extraWatchedKeysChanged(
    previous: Set<String>?,
    current: Set<String>,
): Boolean = previous.orEmpty() != current

private const val maxRestorableWatchedPayloadChars = 4 * 1024 * 1024

internal fun shouldRestoreWatchedPayload(payloadLength: Int): Boolean =
    payloadLength <= maxRestorableWatchedPayloadChars

object WatchedRepository {
    private data class WatchedRefreshOperation(
        val profileId: Int,
        val profileGeneration: Long,
        val sourceOperation: WatchedSourceOperation,
    )

    private const val watchedItemsPageSize = 900
    private const val watchedItemsDeltaPageSize = 900
    private const val watchedDeltaOperationUpsert = "upsert"
    private const val watchedDeltaOperationDelete = "delete"
    private const val watchedDiagnosticSampleLimit = 10

    private val accountScopeLock = SynchronizedObject()
    private var accountScopeJob: Job = SupervisorJob()
    private var accountScope = CoroutineScope(accountScopeJob + Dispatchers.Default)
    private val log = Logger.withTag("WatchedRepository")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    private val _uiState = MutableStateFlow(WatchedUiState())
    val uiState: StateFlow<WatchedUiState> = _uiState.asStateFlow()
    private val _fullyWatchedSeriesKeys = MutableStateFlow<Set<String>>(emptySet())
    val fullyWatchedSeriesKeys: StateFlow<Set<String>> = _fullyWatchedSeriesKeys.asStateFlow()

    private var hasLoaded = false
    private var currentProfileId: Int = 1
    private var profileGeneration: Long = 0L
    private var activeSource: WatchProgressSource = WatchProgressSource.KAPLAY_SYNC
    private var sourceGeneration: Long = 0L
    private val itemsStore = WatchedItemsStore()
    private var kaplayFullyWatchedSeriesKeys: Set<String> = emptySet()
    private var providerFullyWatchedSeriesKeys: MutableMap<TrackingProviderId, Set<String>> = mutableMapOf()
    private var expandedSiblingKeys: Set<String> = emptySet()
    private var providerExtraWatchedKeys: MutableMap<TrackingProviderId, Set<String>> = mutableMapOf()
    private var kaplayHasLoaded: Boolean = false
    private var loadedProviders: MutableSet<TrackingProviderId> = mutableSetOf()
    private var kaplayHasLoadedRemote: Boolean = false
    private var providersLoadedFromRemote: MutableSet<TrackingProviderId> = mutableSetOf()
    private var lastSuccessfulPushEpochMs: Long = 0L
    private var deltaCursorEventId: Long = 0L
    private var deltaInitialized: Boolean = false
    internal var syncAdapter: WatchedSyncAdapter = SupabaseWatchedSyncAdapter
    private var extraKeysObserverJob: Job? = null

    fun ensureLoaded() {
        ensureTrackingProvidersRegistered()
        TrackingProviderRegistry.ensureLoaded()
        TrackingSettingsRepository.ensureLoaded()
        if (!hasLoaded) {
            loadFromDisk(ProfileRepository.activeProfileId)
            activateEffectiveSource(
                effectiveWatchedSource(
                    requestedSource = TrackingSettingsRepository.uiState.value.watchProgressSource,
                    connectedProviderIds = connectedWatchedProviderIds(),
                ),
            )
        }
        startExtraKeysObserverIfNeeded()
    }

    fun onProfileChanged(profileId: Int) {
        if (profileId == currentProfileId && hasLoaded) return
        loadFromDisk(profileId)
    }

    fun clearLocalState() {
        val previousAccountJob = synchronized(accountScopeLock) {
            accountScopeJob.also {
                accountScopeJob = SupervisorJob()
                accountScope = CoroutineScope(accountScopeJob + Dispatchers.Default)
            }
        }
        previousAccountJob.cancel()
        extraKeysObserverJob = null
        hasLoaded = false
        currentProfileId = 1
        profileGeneration += 1L
        activeSource = WatchProgressSource.KAPLAY_SYNC
        sourceGeneration += 1L
        itemsStore.update { kaplayItems, providerItems, dirtyKAPlayKeys, dirtyProviderKeys ->
            kaplayItems.clear()
            providerItems.clear()
            dirtyKAPlayKeys.clear()
            dirtyProviderKeys.clear()
        }
        kaplayFullyWatchedSeriesKeys = emptySet()
        providerFullyWatchedSeriesKeys.clear()
        expandedSiblingKeys = emptySet()
        providerExtraWatchedKeys.clear()
        kaplayHasLoaded = false
        loadedProviders.clear()
        kaplayHasLoadedRemote = false
        providersLoadedFromRemote.clear()
        lastSuccessfulPushEpochMs = 0L
        deltaCursorEventId = 0L
        deltaInitialized = false
        _fullyWatchedSeriesKeys.value = emptySet()
        _uiState.value = WatchedUiState()
    }

    private fun loadFromDisk(profileId: Int) {
        currentProfileId = profileId
        profileGeneration += 1L
        activeSource = WatchProgressSource.KAPLAY_SYNC
        sourceGeneration += 1L
        hasLoaded = true
        itemsStore.update { kaplayItems, providerItems, dirtyKAPlayKeys, dirtyProviderKeys ->
            kaplayItems.clear()
            providerItems.clear()
            dirtyKAPlayKeys.clear()
            dirtyProviderKeys.clear()
        }
        kaplayFullyWatchedSeriesKeys = emptySet()
        providerFullyWatchedSeriesKeys.clear()
        expandedSiblingKeys = emptySet()
        providerExtraWatchedKeys.clear()
        kaplayHasLoaded = true
        loadedProviders.clear()
        kaplayHasLoadedRemote = false
        providersLoadedFromRemote.clear()

        val payload = WatchedStorage.loadPayload(profileId).orEmpty().trim()
        if (payload.isNotEmpty()) {
            val storedPayload = if (shouldRestoreWatchedPayload(payload.length)) {
                runCatching {
                    json.decodeFromString<StoredWatchedPayload>(payload)
                }.getOrDefault(StoredWatchedPayload())
            } else {
                WatchedStorage.savePayload(profileId, "")
                StoredWatchedPayload()
            }
            lastSuccessfulPushEpochMs = storedPayload.lastSuccessfulPushEpochMs
            deltaCursorEventId = storedPayload.deltaCursorEventId
            deltaInitialized = storedPayload.deltaInitialized
            val restoredItems = storedPayload.items
                .map(WatchedItem::normalizedMarkedAt)
                .associateBy { watchedItemKey(it.type, it.id, it.season, it.episode) }
            val restoredProviderPayloads = storedPayload.providerPayloads.mapNotNull { (storageId, providerPayload) ->
                TrackingProviderId.fromStorage(storageId)?.let { providerId -> providerId to providerPayload }
            }.toMap()
            itemsStore.update { kaplayItems, providerItems, dirtyKAPlayKeys, dirtyProviderKeys ->
                kaplayItems.putAll(restoredItems)
                dirtyKAPlayKeys += storedPayload.dirtyWatchedKeys.filter { key -> key in restoredItems }
                restoredProviderPayloads.forEach { (providerId, providerPayload) ->
                    val providerItemsByKey = (providerPayload.items + expandProviderWatchedItems(providerPayload.itemGroups))
                        .map(WatchedItem::normalizedMarkedAt)
                        .associateBy { watchedItemKey(it.type, it.id, it.season, it.episode) }
                    providerItems[providerId] = providerItemsByKey.toMutableMap()
                    dirtyProviderKeys[providerId] = providerPayload.dirtyWatchedKeys
                        .filterTo(mutableSetOf()) { key -> key in providerItemsByKey }
                }
            }
            kaplayFullyWatchedSeriesKeys = storedPayload.fullyWatchedSeriesKeys
            expandedSiblingKeys = storedPayload.expandedSiblingKeys
            restoredProviderPayloads.forEach { (providerId, providerPayload) ->
                providerFullyWatchedSeriesKeys[providerId] = providerPayload.fullyWatchedSeriesKeys
                providerExtraWatchedKeys[providerId] =
                    providerPayload.extraWatchedKeys + expandExtraWatchedKeys(providerPayload.extraWatchedKeyGroups)
            }
            loadedProviders += restoredProviderPayloads.keys
        } else {
            lastSuccessfulPushEpochMs = 0L
            deltaCursorEventId = 0L
            deltaInitialized = false
            kaplayFullyWatchedSeriesKeys = emptySet()
        }

        publish()
    }

    internal fun activateSource(source: WatchProgressSource): WatchProgressSource {
        if (!hasLoaded) {
            loadFromDisk(ProfileRepository.activeProfileId)
        }
        return activateEffectiveSource(source)
    }

    private fun activateEffectiveSource(source: WatchProgressSource): WatchProgressSource {
        if (activeSource == source) {
            log.i {
                "Watched source activation unchanged source=$source generation=$sourceGeneration " +
                    "items=${itemCountForSource(source)} loaded=${hasLoadedSource(source)}"
            }
            return source
        }
        val previousSource = activeSource
        activeSource = source
        sourceGeneration += 1L
        stopExtraKeysObserver()
        log.i {
            "Watched source activated previous=$previousSource current=$source generation=$sourceGeneration " +
                "provider=${source.providerId?.storageId}"
        }
        publish()
        startExtraKeysObserverIfNeeded()
        return source
    }

    private fun newRefreshOperation(profileId: Int): WatchedRefreshOperation? {
        if (ProfileRepository.activeProfileId != profileId) return null
        if (!hasLoaded || currentProfileId != profileId) return null
        return WatchedRefreshOperation(
            profileId = profileId,
            profileGeneration = profileGeneration,
            sourceOperation = WatchedSourceOperation(
                source = activeSource,
                generation = sourceGeneration,
            ),
        )
    }

    private fun isActiveOperation(operation: WatchedRefreshOperation): Boolean =
        currentProfileId == operation.profileId &&
            profileGeneration == operation.profileGeneration &&
            ProfileRepository.activeProfileId == operation.profileId &&
            isWatchedSourceOperationCurrent(
                operation = operation.sourceOperation,
                activeSource = activeSource,
                activeGeneration = sourceGeneration,
            )

    suspend fun pullFromServer(profileId: Int) {
        TrackingProviderRegistry.ensureLoaded()
        TrackingSettingsRepository.ensureLoaded()
        refreshForSource(
            profileId = profileId,
            source = effectiveWatchedSource(
                requestedSource = TrackingSettingsRepository.uiState.value.watchProgressSource,
                connectedProviderIds = connectedWatchedProviderIds(),
            ),
            forceSnapshot = false,
        )
    }

    suspend fun forceSnapshotRefreshFromServer(profileId: Int) {
        TrackingProviderRegistry.ensureLoaded()
        TrackingSettingsRepository.ensureLoaded()
        refreshForSource(
            profileId = profileId,
            source = effectiveWatchedSource(
                requestedSource = TrackingSettingsRepository.uiState.value.watchProgressSource,
                connectedProviderIds = connectedWatchedProviderIds(),
            ),
            forceSnapshot = true,
        )
    }

    internal suspend fun refreshForSource(
        profileId: Int,
        source: WatchProgressSource,
        forceSnapshot: Boolean = true,
    ): Boolean {
        TrackingProviderRegistry.ensureLoaded()
        TrackingSettingsRepository.ensureLoaded()
        if (ProfileRepository.activeProfileId != profileId) {
            log.d { "Skipping watched refresh for inactive profile $profileId" }
            return false
        }
        if (!hasLoaded || currentProfileId != profileId) {
            loadFromDisk(profileId)
        }

        val effectiveSource = activateEffectiveSource(source)
        val operation = newRefreshOperation(profileId) ?: return false
        log.i {
            "Watched refresh request profile=$profileId requestedSource=$source effectiveSource=$effectiveSource " +
                "forceSnapshot=$forceSnapshot profileGeneration=$profileGeneration " +
                "sourceGeneration=$sourceGeneration"
        }
        if (effectiveSource.providerId == null) {
            val authState = AuthRepository.state.value
            if (authState !is AuthState.Authenticated || authState.isAnonymous) {
                // Local watched state is authoritative when this account has no KAPlay upstream.
                kaplayHasLoaded = true
                kaplayHasLoadedRemote = true
                publish()
                return true
            }
        }
        return try {
            effectiveSource.providerId?.let { providerId ->
                val provider = TrackingProviderRegistry.watchedProvider(providerId)
                    ?: run {
                        log.w { "Watched provider missing provider=${providerId.storageId} source=$effectiveSource" }
                        return false
                    }
                pullSnapshotFromAdapter(
                    adapter = provider,
                    operation = operation,
                    profileId = profileId,
                    resetDeltaState = true,
                )
            } ?: if (forceSnapshot) {
                refreshKAPlaySnapshot(
                    operation = operation,
                    profileId = profileId,
                )
            } else {
                pullSupabaseDeltaFromServer(
                    operation = operation,
                    profileId = profileId,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.e(error) { "Failed to refresh watched items from $effectiveSource" }
            false
        }
    }

    private suspend fun refreshKAPlaySnapshot(
        operation: WatchedRefreshOperation,
        profileId: Int,
    ): Boolean {
        val cursorBeforeSnapshot = try {
            syncAdapter.getDeltaCursor(profileId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
        if (!isActiveOperation(operation)) return false

        val applied = pullSnapshotFromAdapter(
            adapter = syncAdapter,
            operation = operation,
            profileId = profileId,
            resetDeltaState = cursorBeforeSnapshot == null,
        )
        if (!applied || !isActiveOperation(operation)) return false
        if (cursorBeforeSnapshot != null) {
            deltaCursorEventId = cursorBeforeSnapshot
            deltaInitialized = true
            persist()
        }
        return true
    }

    private suspend fun pullSnapshotFromAdapter(
        adapter: WatchedSyncAdapter,
        operation: WatchedRefreshOperation,
        profileId: Int,
        resetDeltaState: Boolean,
    ): Boolean {
        val serverItems = adapter.pull(
            profileId = profileId,
            pageSize = watchedItemsPageSize,
        )
        val fullyWatchedSeriesKeys = adapter.pullFullyWatchedSeriesKeys(profileId)
        val extraWatchedKeys = adapter.pullExtraWatchedKeys(profileId)
        val source = operation.sourceOperation.source
        log.i {
            "Watched adapter result source=$source provider=${source.providerId?.storageId ?: "kaplay"} " +
                "profile=$profileId serverItems=${serverItems.size} " +
                "movies=${serverItems.count { it.type == "movie" }} " +
                "episodes=${serverItems.count { it.season != null && it.episode != null }} " +
                "seriesSummaries=${serverItems.count { it.type != "movie" && it.season == null }} " +
                "fullyWatchedSeries=${fullyWatchedSeriesKeys?.size} " +
                "itemKeys=[${diagnosticItemKeySample(serverItems)}] " +
                "fullyWatchedKeys=[${fullyWatchedSeriesKeys.orEmpty().take(watchedDiagnosticSampleLimit).joinToString(",")}]"
        }
        if (!isActiveOperation(operation)) {
            log.w {
                "Watched adapter result discarded source=$source profile=$profileId " +
                    "operationProfileGeneration=${operation.profileGeneration} currentProfileGeneration=$profileGeneration " +
                    "operationSourceGeneration=${operation.sourceOperation.generation} currentSourceGeneration=$sourceGeneration " +
                    "activeSource=$activeSource"
            }
            return false
        }
        itemsStore.update { kaplayItems, providerItems, dirtyKAPlayKeys, dirtyProviderKeys ->
            val items = source.providerId
                ?.let { providerId -> providerItems[providerId]?.values.orEmpty() }
                ?: kaplayItems.values
            val dirtyKeys = source.providerId
                ?.let { providerId -> dirtyProviderKeys.getOrPut(providerId, ::mutableSetOf) }
                ?: dirtyKAPlayKeys
            val merged = mergeWatchedSnapshot(
                serverItems = serverItems,
                localItems = items.toList(),
                dirtyKeys = dirtyKeys,
                acknowledgeDirtyByPresence = source.providerId != null,
            )
            replaceWatchedItemsForSource(
                source = source,
                kaplayItems = kaplayItems,
                providerItems = providerItems,
                replacement = merged.items,
            )
            dirtyKeys.clear()
            dirtyKeys += merged.dirtyKeys
        }
        fullyWatchedSeriesKeys?.let { keys ->
            setFullyWatchedSeriesKeysForSource(source, keys)
        }
        source.providerId?.let { providerId ->
            providerExtraWatchedKeys[providerId] = extraWatchedKeys
            loadedProviders += providerId
            providersLoadedFromRemote += providerId
        } ?: run {
            kaplayHasLoaded = true
            kaplayHasLoadedRemote = true
            if (resetDeltaState) {
                deltaCursorEventId = 0L
                deltaInitialized = false
            }
        }
        publish()
        persist()
        return true
    }

    private suspend fun pullSupabaseDeltaFromServer(
        operation: WatchedRefreshOperation,
        profileId: Int,
    ): Boolean {
        if (!isActiveOperation(operation)) return false
        if (!deltaInitialized) {
            val cursorBeforeSnapshot = try {
                syncAdapter.getDeltaCursor(profileId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                null
            }
            if (!isActiveOperation(operation)) return false
            if (cursorBeforeSnapshot == null) {
                return pullSnapshotFromAdapter(
                    adapter = syncAdapter,
                    operation = operation,
                    profileId = profileId,
                    resetDeltaState = true,
                )
            }
            val applied = pullSnapshotFromAdapter(
                adapter = syncAdapter,
                operation = operation,
                profileId = profileId,
                resetDeltaState = false,
            )
            if (!applied || !isActiveOperation(operation)) return false
            deltaCursorEventId = cursorBeforeSnapshot
            deltaInitialized = true
            persist()
            return true
        }

        var cursor = deltaCursorEventId
        var changed = false

        while (true) {
            val events = syncAdapter.pullDelta(
                profileId = profileId,
                sinceEventId = cursor,
                limit = watchedItemsDeltaPageSize,
            )
            if (!isActiveOperation(operation)) return false
            if (events.isEmpty()) break

            itemsStore.update { kaplayItems, _, dirtyKAPlayKeys, _ ->
                applyWatchedDeltaEvents(
                    targetItems = kaplayItems,
                    dirtyKeys = dirtyKAPlayKeys,
                    events = events,
                )
            }
            cursor = maxOf(cursor, events.maxOf { it.eventId })
            deltaCursorEventId = cursor
            deltaInitialized = true
            changed = true

            if (events.size < watchedItemsDeltaPageSize) break
        }

        if (!isActiveOperation(operation)) return false
        kaplayHasLoaded = true
        val remoteReadinessChanged = !kaplayHasLoadedRemote
        kaplayHasLoadedRemote = true
        if (changed || remoteReadinessChanged) {
            publish()
        }
        if (changed) {
            persist()
        }
        return true
    }

    private fun applyWatchedDeltaEvents(
        targetItems: MutableMap<String, WatchedItem>,
        dirtyKeys: MutableSet<String>,
        events: Collection<WatchedDeltaEvent>,
    ) {
        var upsertCount = 0
        var deleteCount = 0
        var removedCount = 0
        var removedByFallbackKeyCount = 0
        var preservedDirtyCount = 0
        var acknowledgedDirtyCount = 0
        var ignoredCount = 0

        events.forEach { event ->
            val key = watchedItemKey(event.contentType, event.contentId, event.season, event.episode)
            when (event.operation.lowercase()) {
                watchedDeltaOperationUpsert -> {
                    upsertCount += 1
                    val remoteItem = WatchedItem(
                        id = event.contentId,
                        type = event.contentType,
                        name = event.title,
                        season = event.season,
                        episode = event.episode,
                        markedAtEpochMs = normalizeWatchedMarkedAtEpochMs(event.watchedAt),
                    )
                    val localItem = targetItems[key]?.normalizedMarkedAt()
                    if (
                        key in dirtyKeys &&
                        localItem != null &&
                        remoteItem.markedAtEpochMs < localItem.markedAtEpochMs
                    ) {
                        preservedDirtyCount += 1
                    } else {
                        targetItems[key] = remoteItem
                        if (dirtyKeys.remove(key)) {
                            acknowledgedDirtyCount += 1
                        }
                    }
                }
                watchedDeltaOperationDelete -> {
                    deleteCount += 1
                    val matchingKey = if (key in targetItems) {
                        key
                    } else {
                        findWatchedItemStableDeleteKey(
                            targetItems = targetItems,
                            contentId = event.contentId,
                            contentType = event.contentType,
                            season = event.season,
                            episode = event.episode,
                        )
                    }
                    if (matchingKey == null) {
                        return@forEach
                    }
                    if (matchingKey in dirtyKeys) {
                        preservedDirtyCount += 1
                        return@forEach
                    }
                    val removedItem = targetItems.remove(matchingKey)
                    if (removedItem != null) {
                        removedCount += 1
                        if (matchingKey != key) {
                            removedByFallbackKeyCount += 1
                        }
                    }
                }
                else -> {
                    ignoredCount += 1
                }
            }
        }

        log.i {
            "Applied watched delta events total=${events.size} upserts=$upsertCount deletes=$deleteCount " +
                "removed=$removedCount removedByFallbackKey=$removedByFallbackKeyCount " +
                "preservedDirty=$preservedDirtyCount acknowledgedDirty=$acknowledgedDirtyCount " +
                "ignored=$ignoredCount"
        }
    }

    private fun findWatchedItemStableDeleteKey(
        targetItems: Map<String, WatchedItem>,
        contentId: String,
        contentType: String,
        season: Int?,
        episode: Int?,
    ): String? = targetItems.entries.firstOrNull { (_, item) ->
        item.id == contentId &&
            watchedDeleteTypesCompatible(remoteType = contentType, localType = item.type) &&
            item.season == season &&
            item.episode == episode
    }?.key

    private fun watchedDeleteTypesCompatible(remoteType: String, localType: String): Boolean {
        if (remoteType.equals(localType, ignoreCase = true)) return true
        return remoteType.isSeriesLikeWatchedType() && localType.isSeriesLikeWatchedType()
    }

    private fun itemsForSourceSnapshot(source: WatchProgressSource): List<WatchedItem> =
        itemsStore.read { kaplayItems, providerItems, _, _ ->
            val items = source.providerId
                ?.let { providerId -> providerItems[providerId]?.values.orEmpty() }
                ?: kaplayItems.values
            items.toList()
        }

    private fun fullyWatchedSeriesKeysForSource(source: WatchProgressSource): Set<String> =
        source.providerId
            ?.let { providerId -> providerFullyWatchedSeriesKeys[providerId].orEmpty() }
            ?: kaplayFullyWatchedSeriesKeys

    private fun setFullyWatchedSeriesKeysForSource(
        source: WatchProgressSource,
        keys: Set<String>,
    ) {
        source.providerId?.let { providerId ->
            providerFullyWatchedSeriesKeys[providerId] = keys
        } ?: run {
            kaplayFullyWatchedSeriesKeys = keys
        }
    }

    private fun hasLoadedSource(source: WatchProgressSource): Boolean =
        source.providerId?.let(loadedProviders::contains) ?: kaplayHasLoaded

    private fun itemCountForSource(source: WatchProgressSource): Int =
        itemsStore.read { kaplayItems, providerItems, _, _ ->
            source.providerId
                ?.let { providerId -> providerItems[providerId]?.size ?: 0 }
                ?: kaplayItems.size
        }

    fun toggleWatched(item: WatchedItem) {
        ensureLoaded()
        val isMarked = isWatched(
            id = item.id,
            type = item.type,
            season = item.season,
            episode = item.episode,
        )
        if (isMarked) {
            unmarkWatched(item)
        } else {
            markWatched(item)
        }
    }

    fun markWatched(item: WatchedItem) {
        markWatched(listOf(item))
    }

    fun markWatched(items: Collection<WatchedItem>) {
        items.filter { it.isEpisode }.map { it.id }.distinct().forEach {
            ContinueWatchingPreferencesRepository.removeDismissedNextUpKeysForContent(it)
        }
        markWatched(items = items, trackerHistorySync = WatchedTrackerHistorySync.Mirror)
    }

    internal fun markWatchedFromPlaybackCompletion(item: WatchedItem, syncRemote: Boolean = true) {
        markWatched(
            items = listOf(item),
            trackerHistorySync = WatchedTrackerHistorySync.Skip,
            syncRemote = syncRemote,
        )
    }

    internal fun markWatchedLocally(items: Collection<WatchedItem>) {
        markWatched(items = items, trackerHistorySync = WatchedTrackerHistorySync.Skip, syncRemote = false)
    }

    internal fun unmarkWatchedLocally(items: Collection<WatchedItem>) {
        unmarkWatched(items = items, syncRemote = false)
    }

    private fun markWatched(
        items: Collection<WatchedItem>,
        trackerHistorySync: WatchedTrackerHistorySync,
        syncRemote: Boolean = true,
    ) {
        ensureLoaded()
        if (items.isEmpty()) return
        val source = activeSource
        val markedAt = WatchedClock.nowEpochMs()
        val timestampedItems = items.map { watchedItem ->
            watchedItem.copy(markedAtEpochMs = markedAt)
        }
        itemsStore.update { kaplayItems, providerItems, dirtyKAPlayKeys, dirtyProviderKeys ->
            val targetItems = source.providerId
                ?.let { providerId -> providerItems.getOrPut(providerId, ::mutableMapOf) }
                ?: kaplayItems
            val dirtyKeys = source.providerId
                ?.let { providerId -> dirtyProviderKeys.getOrPut(providerId, ::mutableSetOf) }
                ?: dirtyKAPlayKeys
            timestampedItems.forEach { watchedItem ->
                val key = watchedItemKey(watchedItem.type, watchedItem.id, watchedItem.season, watchedItem.episode)
                targetItems[key] = watchedItem
                dirtyKeys += key
            }
        }
        publish()
        persist()
        if (syncRemote) {
            pushMarksToServer(
                items = timestampedItems,
                trackerHistorySync = trackerHistorySync,
                source = source,
            )
        }
    }

    fun unmarkWatched(item: WatchedItem) {
        unmarkWatched(listOf(item))
    }

    fun unmarkWatched(
        id: String,
        type: String,
        season: Int? = null,
        episode: Int? = null,
    ) {
        unmarkWatched(
            listOf(
                WatchedItem(
                    id = id,
                    type = type,
                    name = "",
                    season = season,
                    episode = episode,
                    markedAtEpochMs = 0L,
                ),
            ),
        )
    }

    fun unmarkWatched(items: Collection<WatchedItem>) {
        unmarkWatched(items = items, syncRemote = true)
    }

    private fun unmarkWatched(items: Collection<WatchedItem>, syncRemote: Boolean) {
        ensureLoaded()
        if (items.isEmpty()) return
        val source = activeSource
        val (removedItems, removedExtraKeys) = itemsStore.update { kaplayItems, providerItems, dirtyKAPlayKeys, dirtyProviderKeys ->
            val targetItems = source.providerId
                ?.let { providerId -> providerItems.getOrPut(providerId, ::mutableMapOf) }
                ?: kaplayItems
            val dirtyKeys = source.providerId
                ?.let { providerId -> dirtyProviderKeys.getOrPut(providerId, ::mutableSetOf) }
                ?: dirtyKAPlayKeys
            var extraKeysChanged = false
            val removed = items.mapNotNull { watchedItem ->
                val keys = watchedItemKeys(
                    type = watchedItem.type,
                    id = watchedItem.id,
                    season = watchedItem.season,
                    episode = watchedItem.episode,
                )
                val matchingKey = keys.firstOrNull(targetItems::containsKey)
                source.providerId?.let { providerId ->
                    providerExtraWatchedKeys[providerId]?.let { extraKeys ->
                        val updated = extraKeys - keys
                        if (updated != extraKeys) {
                            providerExtraWatchedKeys[providerId] = updated
                            extraKeysChanged = true
                        }
                    }
                }
                matchingKey?.let(targetItems::remove)?.let { storeItem ->
                    if (watchedItem.videoId != null && storeItem.videoId == null) {
                        storeItem.copy(videoId = watchedItem.videoId)
                    } else {
                        storeItem
                    }
                }?.also { dirtyKeys.remove(matchingKey) }
            }
            removed to extraKeysChanged
        }
        if (removedItems.isNotEmpty()) {
            publish()
            persist()
            if (syncRemote) pushDeleteToServer(items = removedItems, source = source)
        } else if (source.providerId != null) {
            if (removedExtraKeys) {
                publish()
                persist()
            }
            if (syncRemote) pushDeleteToServer(items = items.toList(), source = source)
        }
    }

    fun isWatched(
        id: String,
        type: String,
        season: Int? = null,
        episode: Int? = null,
    ): Boolean {
        ensureLoaded()
        val source = activeSource
        val keys = watchedItemKeys(type = type, id = id, season = season, episode = episode)
        val stored = itemsStore.read { kaplayItems, providerItems, _, _ ->
            source.providerId?.let { providerId ->
                providerItems[providerId]?.let { itemsByKey -> keys.any(itemsByKey::containsKey) } == true
            } ?: keys.any(kaplayItems::containsKey)
        }
        if (stored) return true
        val providerId = source.providerId ?: return false
        return providerExtraWatchedKeys[providerId]?.let { extraKeys -> keys.any(extraKeys::contains) } == true
    }

    fun isFullyWatchedSeries(id: String, type: String): Boolean {
        val keys = watchedItemKeys(type = type, id = id)
        return keys.any(_fullyWatchedSeriesKeys.value::contains)
    }

    fun reconcileSeriesWatchedState(
        meta: MetaDetails,
        todayIsoDate: String,
        isEpisodeCompleted: (com.kaplay.app.features.details.MetaVideo) -> Boolean = { false },
    ) {
        if (!meta.type.isSeriesLikeWatchedType()) return

        ensureLoaded()
        val shouldMarkSeriesWatched = reconcileFullyWatchedSeriesState(
            meta = meta,
            todayIsoDate = todayIsoDate,
            isEpisodeCompleted = isEpisodeCompleted,
        )
        val seriesWatchedItem = meta.toSeriesWatchedItem()
        val hasSeriesWatchedMarker = isWatched(id = meta.id, type = meta.type)
        log.i {
            "Watched series reconciliation source=$activeSource content=${meta.type}:${meta.id} " +
                "episodes=${meta.videos.size} shouldMarkSeries=$shouldMarkSeriesWatched " +
                "hasSeriesMarker=$hasSeriesWatchedMarker " +
                "matchingItems=${itemsForSourceSnapshot(activeSource).count { it.id == meta.id }}"
        }
        if (shouldMarkSeriesWatched) {
            if (!hasSeriesWatchedMarker) {
                markWatched(seriesWatchedItem)
            }
        } else if (hasSeriesWatchedMarker) {
            unmarkWatched(seriesWatchedItem)
        }
    }

    fun reconcileFullyWatchedSeriesState(
        meta: MetaDetails,
        todayIsoDate: String,
        isEpisodeWatched: (MetaVideo) -> Boolean = { episode ->
            val keys = watchedItemKeys(meta.type, meta.id, episode.season, episode.episode)
            if (keys.any(_uiState.value.watchedKeys::contains)) {
                true
            } else {
                val episodeNumber = episode.episode
                if (episodeNumber != null) {
                    com.kaplay.app.features.simkl.SimklAnimeWatchedFallback.isWatched(episode.id, episodeNumber)
                } else {
                    false
                }
            }
        },
        isEpisodeCompleted: (MetaVideo) -> Boolean = { false },
    ): Boolean {
        if (!meta.type.isSeriesLikeWatchedType()) return false

        val shouldMarkSeriesWatched = calculateFullyWatchedSeriesState(
            meta = meta,
            todayIsoDate = todayIsoDate,
            isEpisodeWatched = isEpisodeWatched,
            isEpisodeCompleted = isEpisodeCompleted,
        )
        updateFullyWatchedSeriesStates(
            mapOf(watchedItemKey(meta.type, meta.id) to shouldMarkSeriesWatched),
        )
        return shouldMarkSeriesWatched
    }

    internal fun calculateFullyWatchedSeriesState(
        meta: MetaDetails,
        todayIsoDate: String,
        isEpisodeWatched: (MetaVideo) -> Boolean,
        isEpisodeCompleted: (MetaVideo) -> Boolean,
    ): Boolean {
        if (!meta.type.isSeriesLikeWatchedType()) return false

        ensureLoaded()
        return meta.hasWatchedAllMainSeasonEpisodes(todayIsoDate) { episode ->
            isEpisodeWatched(episode) || isEpisodeCompleted(episode)
        }
    }

    fun updateFullyWatchedSeries(
        id: String,
        type: String,
        isFullyWatched: Boolean,
    ) {
        if (!type.isSeriesLikeWatchedType()) return
        ensureLoaded()
        updateFullyWatchedSeriesKey(
            key = watchedItemKey(type, id),
            isFullyWatched = isFullyWatched,
        )
    }

    private fun updateFullyWatchedSeriesKey(
        key: String,
        isFullyWatched: Boolean,
    ) {
        updateFullyWatchedSeriesStates(mapOf(key to isFullyWatched))
    }

    internal fun updateFullyWatchedSeriesStates(states: Map<String, Boolean>) {
        if (states.isEmpty()) return
        ensureLoaded()
        val source = activeSource
        val current = fullyWatchedSeriesKeysForSource(source)
        val updated = current.toMutableSet().apply {
            states.forEach { (key, isFullyWatched) ->
                if (isFullyWatched) add(key) else remove(key)
            }
        }
        if (updated == current) return
        setFullyWatchedSeriesKeysForSource(source = source, keys = updated)
        publish()
        persist()
    }

    fun setExpandedFullyWatchedSeriesKeys(keys: Set<String>) {
        if (expandedSiblingKeys == keys) return
        expandedSiblingKeys = keys
        publish()
        persist()
    }

    fun currentExpandedSiblingKeys(): Set<String> = expandedSiblingKeys

    /**
     * Returns the base fully-watched series keys from the active source,
     * without sibling expansion. Used by sibling expansion to avoid feedback loops.
     */
    fun baseFullyWatchedSeriesKeys(): Set<String> = fullyWatchedSeriesKeysForSource(activeSource)

    private fun pushMarksToServer(
        items: Collection<WatchedItem>,
        trackerHistorySync: WatchedTrackerHistorySync,
        source: WatchProgressSource,
    ) {
        val profileId = currentProfileId
        val operationGeneration = profileGeneration
        if (trackerHistorySync == WatchedTrackerHistorySync.Mirror) ServerWatched.mirror(items, played = true)
        accountScopeSnapshot().launch {
            runCatching {
                if (items.isEmpty()) return@runCatching
                val outcome = pushToTargetsForSource(
                    profileId = profileId,
                    items = items,
                    trackerHistorySync = trackerHistorySync,
                    source = source,
                )
                if (shouldAcknowledgeKAPlayWatchedPush(source = source, outcome = outcome)) {
                    recordSuccessfulPush(
                        profileId = profileId,
                        operationGeneration = operationGeneration,
                        items = items,
                    )
                }
            }.onFailure { e ->
                log.e(e) { "Failed to push watched items" }
            }
        }
    }

    private fun pushDeleteToServer(
        items: Collection<WatchedItem>,
        source: WatchProgressSource,
    ) {
        val profileId = currentProfileId
        ServerWatched.mirror(items, played = false)
        accountScopeSnapshot().launch {
            runCatching {
                if (items.isEmpty()) return@runCatching
                deleteFromTargetsForSource(
                    profileId = profileId,
                    items = items,
                    source = source,
                )
            }.onFailure { e ->
                log.e(e) { "Failed to push watched item delete" }
            }
        }
    }

    private fun publish() {
        val (kaplayItems, providerItems) = itemsStore.read { storedKAPlayItems, storedProviderItems, _, _ ->
            storedKAPlayItems.values.toList() to storedProviderItems.mapValues { (_, itemsByKey) ->
                itemsByKey.values.toList()
            }
        }
        val items = watchedItemsForSource(
            source = activeSource,
            kaplayItems = kaplayItems,
            providerItems = providerItems,
        )
            .map(WatchedItem::normalizedMarkedAt)
            .sortedByDescending { it.markedAtEpochMs }
        val fullyWatchedSeriesKeys = fullyWatchedSeriesKeysForSource(activeSource)
        val watchedKeys = items.mapTo(linkedSetOf()) {
            watchedItemKey(it.type, it.id, it.season, it.episode)
        }
        // Merge extra watched keys from providers (e.g. Simkl anime alternate IDs)
        activeSource.providerId?.let { providerId ->
            providerExtraWatchedKeys[providerId]?.let { extraKeys -> watchedKeys += extraKeys }
        }
        val isLoaded = hasLoadedSource(activeSource)
        val hasLoadedRemoteItems = activeSource.providerId
            ?.let(providersLoadedFromRemote::contains)
            ?: kaplayHasLoadedRemote
        _fullyWatchedSeriesKeys.value = fullyWatchedSeriesKeys + expandedSiblingKeys
        _uiState.value = WatchedUiState(
            items = items,
            watchedKeys = watchedKeys,
            isLoaded = isLoaded,
            hasLoadedRemoteItems = hasLoadedRemoteItems,
        )
        log.i {
            "Watched publish source=$activeSource provider=${activeSource.providerId?.storageId ?: "kaplay"} " +
                "items=${items.size} keys=${watchedKeys.size} fullyWatchedSeries=${fullyWatchedSeriesKeys.size} " +
                "isLoaded=$isLoaded hasLoadedRemote=$hasLoadedRemoteItems " +
                "itemKeys=[${diagnosticItemKeySample(items)}] " +
                "fullyWatchedKeys=[${fullyWatchedSeriesKeys.take(watchedDiagnosticSampleLimit).joinToString(",")}]"
        }
    }

    private fun diagnosticItemKeySample(items: Collection<WatchedItem>): String = items
        .asSequence()
        .take(watchedDiagnosticSampleLimit)
        .joinToString(separator = ",") { item ->
            watchedItemKey(item.type, item.id, item.season, item.episode)
        }

    /**
     * Observes provider extra watched keys (e.g. Simkl anime alternate IDs).
     * When the provider's snapshot changes (after mutations, syncs), recomputes
     * extra keys, re-pulls watched items, and re-publishes so watchedKeys and
     * items stay reactive and current.
     */
    private fun startExtraKeysObserverIfNeeded() {
        if (extraKeysObserverJob != null) return
        val providerId = activeSource.providerId ?: return
        val adapter = TrackingProviderRegistry.connectedWatchedProviders()
            .firstOrNull { it.providerId == providerId } ?: return
        extraKeysObserverJob = accountScopeSnapshot().launch {
            adapter.observeExtraWatchedKeys(currentProfileId)
                .distinctUntilChanged()
                .collectLatest { extraKeys ->
                    val keysChanged = extraWatchedKeysChanged(
                        previous = providerExtraWatchedKeys[providerId],
                        current = extraKeys,
                    )
                    if (keysChanged) {
                        val freshItems = watchedProviderRefreshOrNull(
                            refresh = {
                                adapter.pull(
                                    profileId = currentProfileId,
                                    pageSize = watchedItemsPageSize,
                                )
                            },
                            onFailure = { error ->
                                log.w(error) { "Failed to refresh watched items from ${providerId.storageId}" }
                            },
                        ) ?: return@collectLatest
                        providerExtraWatchedKeys[providerId] = extraKeys
                        itemsStore.update { _, providerItems, _, dirtyProviderKeys ->
                            val dirtyKeys = dirtyProviderKeys.getOrPut(providerId, ::mutableSetOf)
                            val merged = mergeWatchedSnapshot(
                                serverItems = freshItems,
                                localItems = providerItems[providerId]?.values.orEmpty().toList(),
                                dirtyKeys = dirtyKeys,
                                acknowledgeDirtyByPresence = true,
                            )
                            providerItems[providerId] = merged.items.toMutableMap()
                            dirtyKeys.clear()
                            dirtyKeys += merged.dirtyKeys
                        }
                        loadedProviders += providerId
                        providersLoadedFromRemote += providerId
                        publish()
                        persist()
                    }
                }
        }
    }

    private fun stopExtraKeysObserver() {
        extraKeysObserverJob?.cancel()
        extraKeysObserverJob = null
    }

    private fun persist() {
        val storedPayload = itemsStore.read { kaplayItems, providerItems, dirtyKAPlayKeys, dirtyProviderKeys ->
            val providerIds = buildSet {
                addAll(providerItems.keys)
                addAll(dirtyProviderKeys.keys)
                addAll(providerFullyWatchedSeriesKeys.keys)
                addAll(providerExtraWatchedKeys.keys)
                addAll(loadedProviders)
            }
            StoredWatchedPayload(
                items = kaplayItems.values
                    .map(WatchedItem::normalizedMarkedAt)
                    .sortedByDescending { it.markedAtEpochMs },
                fullyWatchedSeriesKeys = kaplayFullyWatchedSeriesKeys,
                expandedSiblingKeys = expandedSiblingKeys,
                lastSuccessfulPushEpochMs = lastSuccessfulPushEpochMs,
                deltaCursorEventId = deltaCursorEventId,
                deltaInitialized = deltaInitialized,
                dirtyWatchedKeys = dirtyKAPlayKeys.toSet(),
                providerPayloads = providerIds.associate { providerId ->
                    val items = providerItems[providerId]
                        .orEmpty()
                        .values
                        .map(WatchedItem::normalizedMarkedAt)
                    providerId.storageId to StoredProviderWatchedPayload(
                        itemGroups = compactProviderWatchedItems(items),
                        fullyWatchedSeriesKeys = providerFullyWatchedSeriesKeys[providerId].orEmpty(),
                        extraWatchedKeyGroups = compactExtraWatchedKeys(
                            providerExtraWatchedKeys[providerId].orEmpty(),
                        ),
                        dirtyWatchedKeys = dirtyProviderKeys[providerId].orEmpty(),
                    )
                },
            )
        }
        WatchedStorage.savePayload(
            currentProfileId,
            json.encodeToString(storedPayload),
        )
    }

    private fun recordSuccessfulPush(
        profileId: Int,
        operationGeneration: Long,
        items: Collection<WatchedItem>,
    ) {
        if (profileId != currentProfileId || operationGeneration != profileGeneration) return
        val latestPushed = items
            .asSequence()
            .map { item -> normalizeWatchedMarkedAtEpochMs(item.markedAtEpochMs) }
            .maxOrNull()
            ?: return
        val changed = itemsStore.update { kaplayItems, _, dirtyKAPlayKeys, _ ->
            val acknowledgedDirtyKeys = acknowledgeSuccessfulWatchedPush(
                currentItems = kaplayItems,
                dirtyKeys = dirtyKAPlayKeys,
                pushedItems = items,
            )
            val updatedLastSuccessfulPushEpochMs = maxOf(lastSuccessfulPushEpochMs, latestPushed)
            if (
                acknowledgedDirtyKeys == dirtyKAPlayKeys &&
                updatedLastSuccessfulPushEpochMs == lastSuccessfulPushEpochMs
            ) {
                false
            } else {
                dirtyKAPlayKeys.clear()
                dirtyKAPlayKeys += acknowledgedDirtyKeys
                lastSuccessfulPushEpochMs = updatedLastSuccessfulPushEpochMs
                true
            }
        }
        if (changed) persist()
    }

    private suspend fun pushToTargetsForSource(
        profileId: Int,
        items: Collection<WatchedItem>,
        trackerHistorySync: WatchedTrackerHistorySync,
        source: WatchProgressSource,
    ): WatchedPushOutcome {
        val (serverItems, items) = items.partition(ServerWatched::isServerItem)
        if (trackerHistorySync == WatchedTrackerHistorySync.Mirror) ServerWatched.apply(serverItems, played = true)
        if (items.isEmpty()) return WatchedPushOutcome(kaplaySyncSucceeded = true)
        var kaplaySyncSucceeded = false
        val succeededTrackerProviderIds = linkedSetOf<TrackingProviderId>()
        if (source.providerId == null) {
            try {
                syncAdapter.push(profileId = profileId, items = items)
                kaplaySyncSucceeded = true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.e(error) { "Failed to push watched items to KAPlay Sync" }
            }
        }

        if (trackerHistorySync == WatchedTrackerHistorySync.Mirror) {
            TrackingProviderRegistry.connectedWatchedProviders().forEach { provider ->
                try {
                    provider.push(profileId = profileId, items = items)
                    succeededTrackerProviderIds += provider.providerId
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    log.e(error) { "Failed to push watched items to ${provider.providerId.storageId}" }
                }
            }
        }
        return WatchedPushOutcome(
            kaplaySyncSucceeded = kaplaySyncSucceeded,
            succeededTrackerProviderIds = succeededTrackerProviderIds,
        )
    }

    private suspend fun deleteFromTargetsForSource(
        profileId: Int,
        items: Collection<WatchedItem>,
        source: WatchProgressSource,
    ) {
        val (serverItems, items) = items.partition(ServerWatched::isServerItem)
        ServerWatched.apply(serverItems, played = false)
        if (items.isEmpty()) return
        if (source.providerId == null) {
            try {
                syncAdapter.delete(profileId = profileId, items = items)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.e(error) { "Failed to delete watched items from KAPlay Sync" }
            }
        }

        TrackingProviderRegistry.connectedWatchedProviders().forEach { provider ->
            try {
                provider.delete(profileId = profileId, items = items)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.e(error) { "Failed to delete watched items from ${provider.providerId.storageId}" }
            }
        }
    }

    private fun accountScopeSnapshot(): CoroutineScope =
        synchronized(accountScopeLock) {
            accountScope
        }

    private fun connectedWatchedProviderIds(): Set<TrackingProviderId> =
        TrackingProviderRegistry.connectedWatchedProviders()
            .mapTo(linkedSetOf()) { provider -> provider.providerId }
}

internal data class WatchedSnapshotMerge(
    val items: Map<String, WatchedItem>,
    val dirtyKeys: Set<String>,
)

internal fun mergeWatchedSnapshot(
    serverItems: Collection<WatchedItem>,
    localItems: Collection<WatchedItem>,
    dirtyKeys: Set<String>,
    acknowledgeDirtyByPresence: Boolean = false,
): WatchedSnapshotMerge {
    val remoteByKey = serverItems
        .map(WatchedItem::normalizedMarkedAt)
        .associateBy { watchedItemKey(it.type, it.id, it.season, it.episode) }
        .toMutableMap()
    val localByKey = localItems
        .map(WatchedItem::normalizedMarkedAt)
        .associateBy { watchedItemKey(it.type, it.id, it.season, it.episode) }
    val remainingDirtyKeys = dirtyKeys
        .filterTo(mutableSetOf()) { key -> key in localByKey }

    remainingDirtyKeys.toList().forEach { key ->
        val localItem = localByKey.getValue(key)
        val remoteItem = remoteByKey[key]
        if (remoteItem == null) {
            remoteByKey[key] = localItem
        } else if (acknowledgeDirtyByPresence || remoteItem.markedAtEpochMs >= localItem.markedAtEpochMs) {
            remainingDirtyKeys -= key
        } else {
            remoteByKey[key] = localItem
        }
    }

    return WatchedSnapshotMerge(
        items = remoteByKey,
        dirtyKeys = remainingDirtyKeys,
    )
}

internal fun acknowledgeSuccessfulWatchedPush(
    currentItems: Map<String, WatchedItem>,
    dirtyKeys: Set<String>,
    pushedItems: Collection<WatchedItem>,
): Set<String> {
    val remainingDirtyKeys = dirtyKeys.toMutableSet()
    pushedItems
        .map(WatchedItem::normalizedMarkedAt)
        .forEach { pushedItem ->
            val key = watchedItemKey(
                type = pushedItem.type,
                id = pushedItem.id,
                season = pushedItem.season,
                episode = pushedItem.episode,
            )
            val currentItem = currentItems[key]?.normalizedMarkedAt()
            if (currentItem == null || currentItem.markedAtEpochMs <= pushedItem.markedAtEpochMs) {
                remainingDirtyKeys -= key
            }
        }
    return remainingDirtyKeys
}

internal fun effectiveWatchedSource(
    requestedSource: WatchProgressSource,
    connectedProviderIds: Set<TrackingProviderId>,
): WatchProgressSource = effectiveWatchProgressSource(
    requestedSource = requestedSource,
    isProviderAuthenticated = { providerId -> providerId in connectedProviderIds },
)

private fun String.isSeriesLikeWatchedType(): Boolean =
    trim().lowercase() in setOf("series", "show", "tv", "tvshow")
