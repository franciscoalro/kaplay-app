package com.kaplay.app.features.watched

import com.kaplay.app.features.tracking.TrackingProviderId
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

internal class WatchedItemsStore {
    private val lock = SynchronizedObject()
    private val kaplayItems = mutableMapOf<String, WatchedItem>()
    private val providerItems = mutableMapOf<TrackingProviderId, MutableMap<String, WatchedItem>>()
    private val dirtyKAPlayKeys = mutableSetOf<String>()
    private val dirtyProviderKeys = mutableMapOf<TrackingProviderId, MutableSet<String>>()

    fun <T> read(
        block: (
            kaplayItems: Map<String, WatchedItem>,
            providerItems: Map<TrackingProviderId, Map<String, WatchedItem>>,
            dirtyKAPlayKeys: Set<String>,
            dirtyProviderKeys: Map<TrackingProviderId, Set<String>>,
        ) -> T,
    ): T = synchronized(lock) {
        block(kaplayItems, providerItems, dirtyKAPlayKeys, dirtyProviderKeys)
    }

    fun <T> update(
        block: (
            kaplayItems: MutableMap<String, WatchedItem>,
            providerItems: MutableMap<TrackingProviderId, MutableMap<String, WatchedItem>>,
            dirtyKAPlayKeys: MutableSet<String>,
            dirtyProviderKeys: MutableMap<TrackingProviderId, MutableSet<String>>,
        ) -> T,
    ): T = synchronized(lock) {
        block(kaplayItems, providerItems, dirtyKAPlayKeys, dirtyProviderKeys)
    }
}
