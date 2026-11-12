package com.kaplay.app.features.profiles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AvatarAccessTest {
    @Test
    fun supporterAvatarsAreOnlyPublishedWithAccess() {
        val standard = AvatarCatalogItem(id = "standard", storagePath = "standard.png")
        val supporter = AvatarCatalogItem(
            id = "supporter-gold",
            storagePath = "gold.png",
            localImageUrl = "file:///cache/supporter-gold.png",
            memberOnly = true,
        )

        assertEquals(
            listOf(standard),
            availableAvatarCatalog(listOf(standard), listOf(supporter), hasMemberAccess = false),
        )
        assertEquals(
            listOf(standard, supporter),
            availableAvatarCatalog(listOf(standard), listOf(supporter), hasMemberAccess = true),
        )
    }

    @Test
    fun supporterAvatarUsesAuthenticatedLocalAsset() {
        val supporter = AvatarCatalogItem(
            id = "supporter-gold",
            storagePath = "private/gold.png",
            localImageUrl = "file:///cache/supporter-gold.png",
            memberOnly = true,
        )

        assertEquals("file:///cache/supporter-gold.png", avatarImageUrl(supporter))
        assertNull(avatarImageUrl(supporter.copy(localImageUrl = null)))
    }

    @Test
    fun missingRpcIsUnavailable() {
        assertEquals(
            AvatarCatalogLoadStatus.Unavailable,
            classifyAvatarCatalogFailure("PGRST202", "function is missing from schema cache"),
        )
    }

    @Test
    fun networkFailureIsOffline() {
        assertEquals(
            AvatarCatalogLoadStatus.Offline,
            classifyAvatarCatalogFailure(null, "Unable to resolve host build-only.invalid"),
        )
    }

    @Test
    fun unknownFailureIsUnavailable() {
        assertEquals(
            AvatarCatalogLoadStatus.Unavailable,
            classifyAvatarCatalogFailure("PGRST500", "backend rejected request"),
        )
    }

    @Test
    fun unavailableCatalogWithCacheRemainsVisibleButMarkedUnavailable() {
        assertEquals(
            AvatarCatalogUiState.Unavailable,
            avatarCatalogUiState(AvatarCatalogLoadStatus.Unavailable, hasAvatars = true),
        )
    }

    @Test
    fun unavailableCatalogWithoutCacheIsShownAsUnavailable() {
        assertEquals(
            AvatarCatalogUiState.Unavailable,
            avatarCatalogUiState(AvatarCatalogLoadStatus.Unavailable, hasAvatars = false),
        )
    }

    @Test
    fun offlineCatalogWithoutCacheIsShownAsOffline() {
        assertEquals(
            AvatarCatalogUiState.Offline,
            avatarCatalogUiState(AvatarCatalogLoadStatus.Offline, hasAvatars = false),
        )
    }
}
