package com.kaplay.app.features.settings

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import kaplay.composeapp.generated.resources.Res
import kaplay.composeapp.generated.resources.compose_about_made_with
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsCleanTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun settingsRootPageOmitsTapframeAndSupporters() {
        var tapframeText = ""
        compose.setContent {
            tapframeText = stringResource(Res.string.compose_about_made_with)
            LazyColumn {
                settingsRootPage(
                    isTablet = false,
                    onAccountClick = {},
                    onAppearanceClick = {},
                    onPlaybackClick = {},
                    onIntegrationsClick = {},
                    onNotificationsClick = {},
                    onAdvancedClick = {},
                )
            }
        }

        // Tapframe text must not exist on the page
        if (tapframeText.isNotBlank()) {
            compose.onNodeWithText(tapframeText).assertDoesNotExist()
        }
        // Supporters row must not exist on the page
        compose.onNodeWithText("Apoiadores", substring = true).assertDoesNotExist()
    }
}
