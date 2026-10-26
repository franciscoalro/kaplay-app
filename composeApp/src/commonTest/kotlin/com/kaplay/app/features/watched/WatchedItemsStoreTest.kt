package com.kaplay.app.features.watched

import com.kaplay.app.features.tracking.TrackingProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class WatchedItemsStoreTest {
    @Test
    fun `concurrent updates publish coherent item snapshots`() = runBlocking {
        val store = WatchedItemsStore()

        coroutineScope {
            repeat(4) { writer ->
                launch(Dispatchers.Default) {
                    repeat(500) { index ->
                        val key = "$writer:$index"
                        val item = WatchedItem(
                            id = key,
                            type = "movie",
                            name = key,
                            markedAtEpochMs = index.toLong(),
                        )
                        store.update { kaplayItems, providerItems, dirtyKAPlayKeys, dirtyProviderKeys ->
                            kaplayItems[key] = item
                            providerItems
                                .getOrPut(TrackingProviderId.TRAKT, ::mutableMapOf)[key] = item
                            dirtyKAPlayKeys += key
                            dirtyProviderKeys
                                .getOrPut(TrackingProviderId.TRAKT, ::mutableSetOf) += key
                        }
                    }
                }
            }
            repeat(4) {
                launch(Dispatchers.Default) {
                    repeat(500) {
                        store.read { kaplayItems, providerItems, dirtyKAPlayKeys, dirtyProviderKeys ->
                            val kaplayKeys = kaplayItems.keys.toSet()
                            assertEquals(kaplayKeys, providerItems[TrackingProviderId.TRAKT].orEmpty().keys)
                            assertEquals(kaplayKeys, dirtyKAPlayKeys)
                            assertEquals(kaplayKeys, dirtyProviderKeys[TrackingProviderId.TRAKT].orEmpty())
                        }
                    }
                }
            }
        }

        store.read { kaplayItems, providerItems, dirtyKAPlayKeys, dirtyProviderKeys ->
            assertEquals(2_000, kaplayItems.size)
            assertEquals(kaplayItems.keys, providerItems[TrackingProviderId.TRAKT].orEmpty().keys)
            assertEquals(kaplayItems.keys, dirtyKAPlayKeys)
            assertEquals(kaplayItems.keys, dirtyProviderKeys[TrackingProviderId.TRAKT])
        }
    }
}
