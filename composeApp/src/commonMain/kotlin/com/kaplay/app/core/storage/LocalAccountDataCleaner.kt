package com.kaplay.app.core.storage

import com.kaplay.app.core.build.AppFeaturePolicy
import com.kaplay.app.core.sync.SyncManager
import com.kaplay.app.core.sync.ProfileSettingsSync
import com.kaplay.app.core.tracking.ensureTrackingProvidersRegistered
import com.kaplay.app.features.addons.AddonRepository
import com.kaplay.app.features.catalog.CatalogRepository
import com.kaplay.app.features.collection.CollectionMobileSettingsRepository
import com.kaplay.app.features.collection.CollectionRepository
import com.kaplay.app.features.details.MetaDetailsRepository
import com.kaplay.app.features.details.MetaScreenSettingsRepository
import com.kaplay.app.features.home.HomeCatalogSettingsRepository
import com.kaplay.app.features.home.HomeRepository
import com.kaplay.app.features.library.LibraryRepository
import com.kaplay.app.features.membership.MemberAccessRepository
import com.kaplay.app.features.library.LibraryDisplaySettingsRepository
import com.kaplay.app.features.notifications.EpisodeReleaseNotificationsRepository
import com.kaplay.app.features.player.PlayerLaunchStore
import com.kaplay.app.features.player.PlayerSettingsRepository
import com.kaplay.app.features.p2p.P2pSettingsRepository
import com.kaplay.app.features.plugins.PluginRepository
import com.kaplay.app.features.player.SubtitleRepository
import com.kaplay.app.features.profiles.ProfileRepository
import com.kaplay.app.features.profiles.MAX_PROFILES
import com.kaplay.app.features.search.SearchRepository
import com.kaplay.app.features.servers.ServerRepository
import com.kaplay.app.features.settings.ThemeSettingsRepository
import com.kaplay.app.features.streams.StreamContextStore
import com.kaplay.app.features.streams.StreamBadgeSettingsRepository
import com.kaplay.app.features.streams.StreamLaunchStore
import com.kaplay.app.features.streams.StreamsRepository
import com.kaplay.app.features.tracking.TrackingProviderRegistry
import com.kaplay.app.features.tracking.TrackingSettingsRepository
import com.kaplay.app.core.ui.CardDepthStyleRepository
import com.kaplay.app.core.ui.PosterCardStyleRepository
import com.kaplay.app.features.watchprogress.ContinueWatchingPreferencesRepository
import com.kaplay.app.features.watchprogress.ContinueWatchingEnrichmentCache
import com.kaplay.app.features.watchprogress.WatchProgressRepository
import com.kaplay.app.features.watchprogress.WatchProgressSourceCoordinator
import com.kaplay.app.features.watched.WatchedRepository

internal object LocalAccountDataCleaner {
    fun wipe() {
        ensureTrackingProvidersRegistered()
        TrackingProviderRegistry.removeStoredProfiles(1..MAX_PROFILES)
        SyncManager.cancelAccountSync()
        WatchProgressSourceCoordinator.clearLocalState()
        ProfileSettingsSync.clearAccountState()
        ContinueWatchingEnrichmentCache.clearLocalState()
        WatchProgressRepository.clearLocalState()
        WatchedRepository.clearLocalState()
        LibraryRepository.runAccountStorageWipe {
            wipePlatformStorage()
        }

        ProfileRepository.clearInMemory()
        MemberAccessRepository.clearLocalState()
        AddonRepository.clearLocalState()
        ServerRepository.clearLocalState()
        if (AppFeaturePolicy.pluginsEnabled) {
            PluginRepository.clearLocalState()
        }
        HomeRepository.clear()
        HomeCatalogSettingsRepository.clearLocalState()
        MetaScreenSettingsRepository.clearLocalState()
        com.kaplay.app.features.shuffle.EpisodeShuffleRepository.clearLocalState()
        LibraryRepository.clearLocalState()
        LibraryDisplaySettingsRepository.clearLocalState()
        ContinueWatchingPreferencesRepository.clearLocalState()
        EpisodeReleaseNotificationsRepository.clearLocalState()
        CollectionMobileSettingsRepository.clearLocalState()
        CollectionRepository.clearLocalState()
        ThemeSettingsRepository.clearLocalState()
        PosterCardStyleRepository.clearLocalState()
        CardDepthStyleRepository.clearLocalState()
        TrackingProviderRegistry.clearLocalState()
        TrackingSettingsRepository.clearLocalState()
        PlayerSettingsRepository.clearLocalState()
        StreamBadgeSettingsRepository.clearLocalState()
        P2pSettingsRepository.clearLocalState()
        CatalogRepository.clear()
        StreamsRepository.clear()
        MetaDetailsRepository.clear()
        SearchRepository.reset()
        SubtitleRepository.clear()
        PlayerLaunchStore.clear()
        StreamLaunchStore.clear()
        StreamContextStore.clear()
    }

    internal fun wipePlatformStorage(wipeStorage: () -> Unit = PlatformLocalAccountDataCleaner::wipe) {
        try {
            wipeStorage()
        } finally {
            ContinueWatchingEnrichmentCache.clearLocalState()
        }
    }
}

internal expect object PlatformLocalAccountDataCleaner {
    fun wipe()
}
