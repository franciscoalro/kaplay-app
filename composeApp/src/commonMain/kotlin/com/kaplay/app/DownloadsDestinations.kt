package com.kaplay.app

import androidx.compose.runtime.Composable
import com.kaplay.app.features.downloads.DownloadItem
import com.kaplay.app.features.downloads.DownloadsScreen
import com.kaplay.app.navigation.DownloadShowRoute
import com.kaplay.app.navigation.DownloadsRoute
import com.kaplay.app.navigation.KAPlayNavigator

@Composable
internal fun DownloadsDestination(
    route: DownloadsRoute,
    navController: KAPlayNavigator,
    useNativeNavigation: Boolean,
    onOpenDownload: (DownloadItem) -> Unit,
) {
    val onBack = rememberGuardedPopBackStack(navController, route)
    DownloadsScreen(
        onBack = onBack,
        onOpenDownload = onOpenDownload,
        onNavigateToShow = if (useNativeNavigation) {
            { showId, title -> navController.navigate(DownloadShowRoute(showId, title)) }
        } else {
            null
        },
    )
}

@Composable
internal fun DownloadShowDestination(
    route: DownloadShowRoute,
    navController: KAPlayNavigator,
    onOpenDownload: (DownloadItem) -> Unit,
) {
    val onBack = rememberGuardedPopBackStack(navController, route)
    DownloadsScreen(
        onBack = onBack,
        onOpenDownload = onOpenDownload,
        initialShowId = route.showId,
        onBackFromShow = onBack,
    )
}
