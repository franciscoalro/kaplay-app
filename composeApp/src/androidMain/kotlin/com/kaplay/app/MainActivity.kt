package com.kaplay.app

import android.content.Intent
import android.content.res.Configuration
import com.kaplay.app.features.autosync.AutoSyncPreferencesAndroid
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember

import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.kaplay.app.core.auth.AuthStorage
import com.kaplay.app.core.network.ServerConfigurationStorage
import com.kaplay.app.core.diagnostics.SentryInitializer
import com.kaplay.app.core.deeplink.handleAppUrl
import com.kaplay.app.core.storage.PlatformLocalAccountDataCleaner
import com.kaplay.app.core.sync.SyncClientIdentityStorage
import com.kaplay.app.features.addons.AddonHttpClientProvider
import com.kaplay.app.features.addons.AddonStorage
import com.kaplay.app.features.collection.CollectionMobileSettingsStorage
import com.kaplay.app.features.collection.CollectionStorage
import com.kaplay.app.features.debrid.DebridSettingsStorage
import com.kaplay.app.features.downloads.DownloadsLiveStatusPlatform
import com.kaplay.app.features.downloads.DownloadsPlatformDownloader
import com.kaplay.app.features.downloads.DownloadsStorage
import com.kaplay.app.features.library.LibraryDisplaySettingsStorage
import com.kaplay.app.features.membership.MemberAssetStorage
import com.kaplay.app.features.library.LibraryStorage
import com.kaplay.app.features.details.MetaScreenSettingsStorage
import com.kaplay.app.features.home.HomeCatalogSettingsStorage
import com.kaplay.app.features.mdblist.MdbListSettingsStorage
import com.kaplay.app.features.notifications.EpisodeReleaseNotificationPlatform
import com.kaplay.app.features.notifications.EpisodeReleaseNotificationsStorage
import com.kaplay.app.features.player.PlayerSettingsStorage
import com.kaplay.app.features.player.PlayerTrackPreferenceStorage
import com.kaplay.app.features.player.ExternalPlayerPlatform
import com.kaplay.app.features.player.SubtitleFileCache
import com.kaplay.app.features.player.PlayerPictureInPictureManager
import com.kaplay.app.features.player.PipRemoteActionReceiver
import com.kaplay.app.features.p2p.P2pSettingsStorage
import com.kaplay.app.features.p2p.P2pStreamingEngine
import com.kaplay.app.features.plugins.PluginStorage
import com.kaplay.app.features.profiles.AvatarStorage
import com.kaplay.app.features.profiles.ProfilePinCacheStorage
import com.kaplay.app.features.profiles.ProfileStorage
import com.kaplay.app.features.details.SeasonViewModeStorage
import com.kaplay.app.features.search.DiscoverSelectionStorage
import com.kaplay.app.features.search.SearchHistoryStorage
import com.kaplay.app.features.settings.SentrySettingsStorage
import com.kaplay.app.features.settings.AppIconPlatform
import com.kaplay.app.features.settings.ThemeSettingsStorage
import com.kaplay.app.features.trakt.TraktAuthStorage
import com.kaplay.app.features.trakt.TraktCommentsStorage
import com.kaplay.app.features.trakt.TraktLibraryStorage
import com.kaplay.app.features.trakt.TraktSettingsStorage
import com.kaplay.app.features.mdblist.PlatformMdbListAuthPersistence
import com.kaplay.app.features.mdblist.PlatformMdbListSyncStorage
import com.kaplay.app.features.servers.ServerStorage
import com.kaplay.app.features.simkl.SimklAuthStorage
import com.kaplay.app.features.simkl.SimklSyncStorage
import com.kaplay.app.features.tmdb.TmdbSettingsStorage
import com.kaplay.app.features.updater.AndroidAppUpdaterPlatform
import com.kaplay.app.core.ui.CardDepthStyleStorage
import com.kaplay.app.core.ui.PosterCardStyleStorage
import com.kaplay.app.core.poster.CustomPosterUrlStorage
import com.kaplay.app.features.watched.WatchedStorage
import com.kaplay.app.features.streams.StreamLinkCacheStorage
import com.kaplay.app.features.streams.StreamBadgeSettingsStorage
import com.kaplay.app.features.streams.BingeGroupCacheStorage
import com.kaplay.app.features.watchprogress.ContinueWatchingEnrichmentStorage
import com.kaplay.app.features.watchprogress.ContinueWatchingPreferencesStorage
import com.kaplay.app.features.watchprogress.ResumePromptStorage
import com.kaplay.app.features.watchprogress.WatchProgressStorage
import com.kaplay.app.features.rewards.AdRewardController
import com.kaplay.app.features.rewards.RewardStorage
import com.kaplay.app.features.rewards.RewardedAdsPlatform

open class MainActivity : AppCompatActivity() {
    internal companion object {
        var current: MainActivity? = null
    }

    private var pipRemoteActionReceiver: PipRemoteActionReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.dark(
                scrim = 0xFF020404.toInt(),
            ),
        )
        ThemeSettingsStorage.initialize(applicationContext)
        AppIconPlatform.initialize(applicationContext)
        SentrySettingsStorage.initialize(applicationContext)
        SentryInitializer.start(application)
        super.onCreate(savedInstanceState)
        current = this
        RewardStorage.initialize(applicationContext)
        RewardedAdsPlatform.bind()
        AdRewardController.initialize()
        AutoSyncPreferencesAndroid.initialize(applicationContext) // AutoSync hook
        window.setBackgroundDrawableResource(R.color.kaplay_background)
        pipRemoteActionReceiver = PipRemoteActionReceiver.register(this)
        SyncClientIdentityStorage.initialize(applicationContext)
        AddonHttpClientProvider.initialize(applicationContext)
        AddonStorage.initialize(applicationContext)
        AuthStorage.initialize(applicationContext)
        ServerConfigurationStorage.initialize(applicationContext)
        LibraryStorage.initialize(applicationContext)
        WatchedStorage.initialize(applicationContext)
        MetaScreenSettingsStorage.initialize(applicationContext)
        com.kaplay.app.features.shuffle.EpisodeShuffleStorage.initialize(applicationContext)
        HomeCatalogSettingsStorage.initialize(applicationContext)
        PlayerSettingsStorage.initialize(applicationContext)
        PlayerTrackPreferenceStorage.initialize(applicationContext)
        P2pSettingsStorage.initialize(applicationContext)
        P2pStreamingEngine.initialize(applicationContext)
        ExternalPlayerPlatform.initialize(applicationContext)
        SubtitleFileCache.initialize(applicationContext)
        ProfileStorage.initialize(applicationContext)
        AvatarStorage.initialize(applicationContext)
        ProfilePinCacheStorage.initialize(applicationContext)
        MemberAssetStorage.initialize(applicationContext)
        DiscoverSelectionStorage.initialize(applicationContext)
        SearchHistoryStorage.initialize(applicationContext)
        SeasonViewModeStorage.initialize(applicationContext)
        PosterCardStyleStorage.initialize(applicationContext)
        CustomPosterUrlStorage.initialize(applicationContext)
        CardDepthStyleStorage.initialize(applicationContext)
        DebridSettingsStorage.initialize(applicationContext)
        TmdbSettingsStorage.initialize(applicationContext)
        MdbListSettingsStorage.initialize(applicationContext)
        TraktAuthStorage.initialize(applicationContext)
        TraktCommentsStorage.initialize(applicationContext)
        TraktLibraryStorage.initialize(applicationContext)
        TraktSettingsStorage.initialize(applicationContext)
        PlatformMdbListAuthPersistence.initialize(applicationContext)
        ServerStorage.initialize(applicationContext)
        PlatformMdbListSyncStorage.initialize(applicationContext)
        SimklAuthStorage.initialize(applicationContext)
        SimklSyncStorage.initialize(applicationContext)
        LibraryDisplaySettingsStorage.initialize(applicationContext)
        ContinueWatchingPreferencesStorage.initialize(applicationContext)
        ResumePromptStorage.initialize(applicationContext)
        ContinueWatchingEnrichmentStorage.initialize(applicationContext)
        EpisodeReleaseNotificationsStorage.initialize(applicationContext)
        WatchProgressStorage.initialize(applicationContext)
        StreamLinkCacheStorage.initialize(applicationContext)
        StreamBadgeSettingsStorage.initialize(applicationContext)
        BingeGroupCacheStorage.initialize(applicationContext)
        PluginStorage.initialize(applicationContext)
        CollectionMobileSettingsStorage.initialize(applicationContext)
        CollectionStorage.initialize(applicationContext)
        DownloadsStorage.initialize(applicationContext)
        DownloadsPlatformDownloader.initialize(applicationContext)
        DownloadsLiveStatusPlatform.initialize(applicationContext)
        AndroidAppUpdaterPlatform.initialize(applicationContext)
        PlatformLocalAccountDataCleaner.initialize(applicationContext)
        EpisodeReleaseNotificationPlatform.initialize(applicationContext)
        EpisodeReleaseNotificationPlatform.bindActivity(this)
        handleIncomingAppIntent(intent)

        setContent {
            val appGateController = remember { AppGateController() }
            App(appGateController = appGateController)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingAppIntent(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        PlayerPictureInPictureManager.onUserLeaveHint(this)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PlayerPictureInPictureManager.onPictureInPictureModeChanged(this, isInPictureInPictureMode)
    }

    override fun onDestroy() {
        RewardedAdsPlatform.unbind()
        if (current === this) current = null
        EpisodeReleaseNotificationPlatform.unbindActivity(this)
        val receiver = pipRemoteActionReceiver
        if (receiver != null) {
            runCatching { unregisterReceiver(receiver) }
            pipRemoteActionReceiver = null
        }
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        if (EpisodeReleaseNotificationPlatform.handlePermissionRequestResult(requestCode, grantResults)) {
            return
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun handleIncomingAppIntent(intent: Intent?) {
        val appUrl = (intent?.dataString ?: intent?.getStringExtra(LinkActivity.EXTRA_LINK))?.trim().orEmpty()
        if (appUrl.isBlank()) return
        handleAppUrl(appUrl)
    }
}
