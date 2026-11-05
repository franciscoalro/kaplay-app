package com.kaplay.app.features.details

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kaplay.app.features.details.components.KASeasonDropdown
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DetailSeasonDropdownTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun seasonDropdownDisplaysSelectedSeasonAndSelectsNewOne() {
        var selectedSeason = 1
        compose.setContent {
            KASeasonDropdown(
                seasons = listOf(1, 2, 3),
                selectedSeason = selectedSeason,
                onSeasonSelected = { selectedSeason = it },
            )
        }

        // Initially displays selected season
        compose.onNodeWithText("1", substring = true).assertIsDisplayed()

        // Clicking opens dropdown and displays other seasons
        compose.onNodeWithText("1", substring = true).performClick()

        // Click season 3
        compose.onNodeWithText("3", substring = true).performClick()

        compose.runOnIdle {
            assertEquals(3, selectedSeason)
        }
    }
}
