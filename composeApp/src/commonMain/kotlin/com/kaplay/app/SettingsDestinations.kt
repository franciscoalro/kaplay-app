package com.kaplay.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.kaplay.app.features.collection.CollectionEditorPage
import com.kaplay.app.features.collection.CollectionEditorScreen
import com.kaplay.app.features.collection.CollectionManagementScreen
import com.kaplay.app.features.collection.CollectionRepository
import com.kaplay.app.features.collection.FolderDetailRepository
import com.kaplay.app.features.collection.FolderDetailScreen
import com.kaplay.app.features.home.HomeCatalogSection
import com.kaplay.app.features.settings.SettingsScreen
import com.kaplay.app.navigation.AppRoute
import com.kaplay.app.navigation.CollectionEditorPageRoute
import com.kaplay.app.navigation.CollectionEditorRoute
import com.kaplay.app.navigation.CollectionsRoute
import com.kaplay.app.navigation.FolderDetailRoute
import com.kaplay.app.navigation.KAPlayNavigator
import com.kaplay.app.navigation.PartyRoute
import com.kaplay.app.navigation.SettingsPageRoute
import kaplay.composeapp.generated.resources.Res
import kaplay.composeapp.generated.resources.party_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SettingsDestination(
    route: AppRoute,
    navController: KAPlayNavigator,
    content: @Composable (onBack: () -> Unit) -> Unit,
) {
    val onBack = rememberGuardedPopBackStack(navController, route)
    content(onBack)
}

@Composable
internal fun SettingsRootDestination(
    route: SettingsPageRoute,
    navController: KAPlayNavigator,
    useNativeNavigation: Boolean,
    collectionsTitle: String,
    onCheckForUpdates: (() -> Unit)?,
    onTestUpdateBanner: (() -> Unit)?,
) {
    val onBack = rememberGuardedPopBackStack(navController, route)
    val watchPartyTitle = stringResource(Res.string.party_title)
    SettingsScreen(
        modifier = Modifier.fillMaxSize(),
        initialPageName = route.pageName,
        rootActionsEnabled = false,
        onNavigatePage = { pageName, title ->
            navController.navigate(SettingsPageRoute(pageName, title))
        },
        onExternalBack = onBack,
        showInternalHeader = !useNativeNavigation,
        onCollectionsClick = {
            navController.navigate(CollectionsRoute(collectionsTitle))
        },
        onWatchPartyClick = {
            navController.navigate(PartyRoute(title = watchPartyTitle))
        },
        onCheckForUpdatesClick = onCheckForUpdates,
        onTestUpdateBannerClick = onTestUpdateBanner,
    )
}

@Composable
internal fun CollectionsDestination(
    route: CollectionsRoute,
    navController: KAPlayNavigator,
    newCollectionTitle: String,
) {
    val onBack = rememberGuardedPopBackStack(navController, route)
    CollectionManagementScreen(
        onBack = onBack,
        onNavigateToEditor = { collectionId ->
            val editorTitle = collectionId
                ?.let { id ->
                    CollectionRepository.collections.value.firstOrNull { it.id == id }?.title
                }
                .orEmpty()
            navController.navigate(
                CollectionEditorRoute(
                    collectionId = collectionId,
                    title = editorTitle.ifBlank { newCollectionTitle },
                ),
            )
        },
    )
}

@Composable
internal fun CollectionEditorDestination(
    route: CollectionEditorRoute,
    navController: KAPlayNavigator,
    useNativeNavigation: Boolean,
) {
    val onBack = rememberGuardedPopBackStack(navController, route)
    CollectionEditorScreen(
        collectionId = route.collectionId,
        onBack = onBack,
        initialPage = if (useNativeNavigation) CollectionEditorPage.Root else null,
        onNavigateToPage = if (useNativeNavigation) {
            { page, title ->
                navController.navigate(
                    CollectionEditorPageRoute(
                        collectionId = route.collectionId,
                        pageName = page.name,
                        title = title,
                    ),
                )
            }
        } else {
            null
        },
    )
}

@Composable
internal fun CollectionEditorPageDestination(
    route: CollectionEditorPageRoute,
    navController: KAPlayNavigator,
) {
    val page = remember(route.pageName) {
        runCatching { CollectionEditorPage.valueOf(route.pageName) }.getOrNull()
    }
    val onBack = rememberGuardedPopBackStack(navController, route)
    if (page == null || page == CollectionEditorPage.Root) {
        LaunchedEffect(route) { onBack() }
        return
    }
    CollectionEditorScreen(
        collectionId = route.collectionId,
        initialPage = page,
        initializeRepository = false,
        onBack = onBack,
        onNavigateToPage = { nextPage, title ->
            navController.navigate(
                CollectionEditorPageRoute(
                    collectionId = route.collectionId,
                    pageName = nextPage.name,
                    title = title,
                ),
            )
        },
    )
}

@Composable
internal fun FolderDestination(
    route: FolderDetailRoute,
    navController: KAPlayNavigator,
    onCatalogClick: (HomeCatalogSection) -> Unit,
) {
    val onBack = rememberGuardedPopBackStack(navController, route)
    LaunchedEffect(route.collectionId, route.folderId) {
        FolderDetailRepository.initialize(route.collectionId, route.folderId)
    }
    FolderDetailScreen(
        onBack = onBack,
        onCatalogClick = onCatalogClick,
        onPosterClick = rememberOpenMeta(navController),
    )
}
