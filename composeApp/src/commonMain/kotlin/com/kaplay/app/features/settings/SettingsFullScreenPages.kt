package com.kaplay.app.features.settings

import com.kaplay.app.core.build.AppFeaturePolicy
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaplay.app.core.ui.KAPlayScreen
import com.kaplay.app.core.ui.KAPlayScreenHeader
import com.kaplay.app.features.addons.AddonRepository
import com.kaplay.app.features.addons.enabledAddons
import com.kaplay.app.features.addons.firstEnabledManifestError
import com.kaplay.app.features.addons.hasPendingEnabledManifests
import com.kaplay.app.features.addons.isWaitingForFirstEnabledManifest
import com.kaplay.app.features.collection.CollectionRepository
import com.kaplay.app.features.details.MetaScreenSettingsRepository
import com.kaplay.app.features.plugins.PluginRepository
import com.kaplay.app.features.home.HomeCatalogSettingsRepository
import com.kaplay.app.features.home.buildAddonCatalogRefreshSignature
import com.kaplay.app.features.watchprogress.ContinueWatchingPreferencesRepository
import kaplay.composeapp.generated.resources.Res
import kaplay.composeapp.generated.resources.compose_settings_page_account
import kaplay.composeapp.generated.resources.compose_settings_page_addons
import kaplay.composeapp.generated.resources.compose_settings_page_continue_watching
import kaplay.composeapp.generated.resources.compose_settings_page_homescreen
import kaplay.composeapp.generated.resources.compose_settings_page_meta_screen
import kaplay.composeapp.generated.resources.compose_settings_page_plugins
import org.jetbrains.compose.resources.stringResource

@Composable
fun HomescreenSettingsScreen(
    onBack: () -> Unit,
) {
    val addonsUiState by AddonRepository.uiState.collectAsStateWithLifecycle()
    val homescreenCatalogRefreshKey = remember(addonsUiState.addons) {
        buildAddonCatalogRefreshSignature(addonsUiState.addons)
    }
    val addonManifestsLoading = addonsUiState.addons.hasPendingEnabledManifests()
    val addonManifestErrorMessage = addonsUiState.addons.firstEnabledManifestError()
    val homescreenSettingsUiState by remember {
        HomeCatalogSettingsRepository.snapshot()
        HomeCatalogSettingsRepository.uiState
    }.collectAsStateWithLifecycle()
    val collections by CollectionRepository.collections.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        AddonRepository.initialize()
        CollectionRepository.initialize()
    }

    LaunchedEffect(homescreenCatalogRefreshKey) {
        val enabledAddons = addonsUiState.addons.enabledAddons()
        if (!enabledAddons.isWaitingForFirstEnabledManifest()) {
            HomeCatalogSettingsRepository.syncCatalogs(enabledAddons)
        }
    }

    LaunchedEffect(collections) {
        HomeCatalogSettingsRepository.syncCollections(collections)
    }

    KAPlayScreen(
        modifier = Modifier.fillMaxSize(),
    ) {
        stickyHeader {
            KAPlayScreenHeader(
                title = stringResource(Res.string.compose_settings_page_homescreen),
                onBack = onBack,
            )
        }
        homescreenSettingsContent(
            isTablet = false,
            heroEnabled = homescreenSettingsUiState.heroEnabled,
            showCatalogType = homescreenSettingsUiState.showCatalogType,
            hideUnreleasedContent = homescreenSettingsUiState.hideUnreleasedContent,
            items = homescreenSettingsUiState.items,
            isCatalogLoading = addonManifestsLoading,
            catalogErrorMessage = addonManifestErrorMessage,
        )
    }
}

@Composable
fun MetaScreenSettingsScreen(
    onBack: () -> Unit,
) {
    val metaScreenSettingsUiState by remember {
        MetaScreenSettingsRepository.ensureLoaded()
        MetaScreenSettingsRepository.uiState
    }.collectAsStateWithLifecycle()

    KAPlayScreen(
        modifier = Modifier.fillMaxSize(),
    ) {
        stickyHeader {
            KAPlayScreenHeader(
                title = stringResource(Res.string.compose_settings_page_meta_screen),
                onBack = onBack,
            )
        }
        metaScreenSettingsContent(
            isTablet = false,
            uiState = metaScreenSettingsUiState,
        )
    }
}

@Composable
fun ContinueWatchingSettingsScreen(
    onBack: () -> Unit,
) {
    val continueWatchingPreferencesUiState by remember {
        ContinueWatchingPreferencesRepository.ensureLoaded()
        ContinueWatchingPreferencesRepository.uiState
    }.collectAsStateWithLifecycle()

    KAPlayScreen(
        modifier = Modifier.fillMaxSize(),
    ) {
        stickyHeader {
            KAPlayScreenHeader(
                title = stringResource(Res.string.compose_settings_page_continue_watching),
                onBack = onBack,
            )
        }
        continueWatchingSettingsContent(
            isTablet = false,
            isVisible = continueWatchingPreferencesUiState.isVisible,
            style = continueWatchingPreferencesUiState.style,
            upNextFromFurthestEpisode = continueWatchingPreferencesUiState.upNextFromFurthestEpisode,
            useEpisodeThumbnails = continueWatchingPreferencesUiState.useEpisodeThumbnails,
            showUnairedNextUp = continueWatchingPreferencesUiState.showUnairedNextUp,
            blurNextUp = continueWatchingPreferencesUiState.blurNextUp,
            showResumePromptOnLaunch = continueWatchingPreferencesUiState.showResumePromptOnLaunch,
            sortMode = continueWatchingPreferencesUiState.sortMode,
        )
    }
}

@Composable
fun AddonsSettingsScreen(
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) {
        AddonRepository.initialize()
    }

    KAPlayScreen(
        modifier = Modifier.fillMaxSize(),
    ) {
        stickyHeader {
            KAPlayScreenHeader(
                title = stringResource(Res.string.compose_settings_page_addons),
                onBack = onBack,
            )
        }
        addonsSettingsContent()
    }
}

@Composable
fun PluginsSettingsScreen(
    onBack: () -> Unit,
) {
    if (!AppFeaturePolicy.pluginsEnabled) {
        AddonsSettingsScreen(onBack = onBack)
        return
    }

    LaunchedEffect(Unit) {
        PluginRepository.initialize()
    }

    KAPlayScreen(
        modifier = Modifier.fillMaxSize(),
    ) {
        stickyHeader {
            KAPlayScreenHeader(
                title = stringResource(Res.string.compose_settings_page_plugins),
                onBack = onBack,
            )
        }
        pluginsSettingsContent()
    }
}

@Composable
fun AccountSettingsScreen(
    onBack: () -> Unit,
) {
    KAPlayScreen(
        modifier = Modifier.fillMaxSize(),
    ) {
        stickyHeader {
            KAPlayScreenHeader(
                title = stringResource(Res.string.compose_settings_page_account),
                onBack = onBack,
            )
        }
        accountSettingsContent(
            isTablet = false,
        )
    }
}
