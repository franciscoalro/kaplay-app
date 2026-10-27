package com.kaplay.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.kaplay.app.core.ui.KAPlayToastController
import com.kaplay.app.features.party.PartyMedia
import com.kaplay.app.features.player.ExternalPlayerIntentResult
import com.kaplay.app.features.player.ExternalPlayerPlatform
import com.kaplay.app.features.player.PlayerLaunch
import com.kaplay.app.features.player.PlayerLaunchStore
import com.kaplay.app.features.player.PlayerScreen
import com.kaplay.app.features.watchprogress.ResumePromptRepository
import com.kaplay.app.navigation.DetailRoute
import com.kaplay.app.navigation.KAPlayNavigator
import com.kaplay.app.navigation.PlayerRoute

@Composable
internal fun PlayerDestination(
    route: PlayerRoute,
    navController: KAPlayNavigator,
    externalPlayerId: String?,
    externalPlayerNotConfiguredText: String,
    externalPlayerFailedText: String,
    onExternalPlayerLaunch: (PlayerLaunch) -> Unit,
    launchExternalPlayer: (ExternalPlayerIntentResult.Success) -> Boolean,
    openExternalStreamUrl: (String) -> Boolean,
    onOpenPartyMedia: (PartyMedia) -> Unit,
) {
    val guardedBack = rememberGuardedPopBackStack(navController, route)
    val launch = remember(route.launchId) { PlayerLaunchStore.get(route.launchId) }
    val onBack = remember(route.launchId, launch, guardedBack) {
        {
            guardedBack()
            if (navController.currentRoute == route && launch != null) {
                val parentMetaId = launch.parentMetaId
                val parentMetaType = launch.parentMetaType
                if (!parentMetaId.isNullOrBlank() && !parentMetaType.isNullOrBlank()) {
                    navController.navigate(
                        DetailRoute(
                            type = parentMetaType,
                            id = parentMetaId,
                            title = launch.title,
                        ),
                    )
                }
            }
        }
    }
    if (launch == null) {
        LaunchedEffect(route.launchId) {
            onBack()
        }
        Box(modifier = Modifier.fillMaxSize())
        return
    }
    LaunchedEffect(launch.videoId) {
        launch.videoId?.let { ResumePromptRepository.markPlayerEntered(it) }
    }
    PlayerScreen(
        profileId = launch.profileId,
        title = launch.title,
        sourceUrl = launch.sourceUrl,
        sourceAudioUrl = launch.sourceAudioUrl,
        sourceHeaders = launch.sourceHeaders,
        sourceResponseHeaders = launch.sourceResponseHeaders,
        externalSubtitles = launch.externalSubtitles,
        streamType = launch.streamType,
        logo = launch.logo,
        poster = launch.poster,
        background = launch.background,
        seasonNumber = launch.seasonNumber,
        episodeNumber = launch.episodeNumber,
        episodeTitle = launch.episodeTitle,
        episodeThumbnail = launch.episodeThumbnail,
        streamTitle = launch.streamTitle,
        streamSubtitle = launch.streamSubtitle,
        initialBingeGroup = launch.bingeGroup,
        pauseDescription = launch.pauseDescription,
        providerName = launch.providerName,
        providerAddonId = launch.providerAddonId,
        contentType = launch.contentType,
        videoId = launch.videoId,
        parentMetaId = launch.parentMetaId,
        parentMetaType = launch.parentMetaType,
        torrentInfoHash = launch.torrentInfoHash,
        torrentFileIdx = launch.torrentFileIdx,
        torrentFilename = launch.torrentFilename,
        torrentTrackers = launch.torrentTrackers,
        initialPositionMs = launch.initialPositionMs,
        initialProgressFraction = launch.initialProgressFraction,
        contentLanguage = launch.contentLanguage,
        launchId = route.launchId,
        sourceFilename = launch.sourceFilename,
        stream = launch.stream,
        onBack = onBack,
        onOpenInExternalPlayer = { request ->
            val playerLaunch = PlayerLaunch(
                profileId = launch.profileId,
                title = launch.title,
                sourceUrl = request.sourceUrl,
                sourceHeaders = request.sourceHeaders,
                logo = launch.logo,
                poster = launch.poster,
                background = launch.background,
                seasonNumber = launch.seasonNumber,
                episodeNumber = launch.episodeNumber,
                episodeTitle = launch.episodeTitle,
                episodeThumbnail = launch.episodeThumbnail,
                streamTitle = request.streamTitle ?: launch.streamTitle,
                streamSubtitle = launch.streamSubtitle,
                bingeGroup = launch.bingeGroup,
                pauseDescription = launch.pauseDescription,
                providerName = launch.providerName,
                providerAddonId = launch.providerAddonId,
                contentType = launch.contentType,
                videoId = launch.videoId,
                parentMetaId = launch.parentMetaId,
                parentMetaType = launch.parentMetaType,
                initialPositionMs = request.resumePositionMs,
            )
            onExternalPlayerLaunch(playerLaunch)
            val intentResult = ExternalPlayerPlatform.buildIntent(
                request = request,
                playerId = externalPlayerId,
            )
            when (intentResult) {
                is ExternalPlayerIntentResult.Success -> {
                    val launched = launchExternalPlayer(intentResult)
                    if (!launched) {
                        KAPlayToastController.show(externalPlayerFailedText)
                    } else if (externalPlayerId == "infuse") {
                        onBack()
                    }
                }
                ExternalPlayerIntentResult.NotConfigured -> {
                    KAPlayToastController.show(externalPlayerNotConfiguredText)
                }
                ExternalPlayerIntentResult.Failed -> {
                    KAPlayToastController.show(externalPlayerFailedText)
                }
            }
        },
        onOpenExternalUrl = { url ->
            openExternalStreamUrl(url)
        },
        onOpenPartyMedia = onOpenPartyMedia,
        modifier = Modifier.fillMaxSize(),
    )
}
