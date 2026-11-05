package com.kaplay.app.features.details

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.kaplay.app.core.ui.KAColors
import com.kaplay.app.core.ui.KAShapes
import com.kaplay.app.features.details.components.DetailSeriesListHeader
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DetailSeriesContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun seriesContentTokensMatchDesignSystemSpec() {
        assertEquals(8.dp, KAShapes.ChipRadius)
        assertEquals(10.dp, KAShapes.CardRadius)
        assertEquals(Color(0xFF9729D7), KAColors.Primary)
        assertEquals(Color(0xFF23232B), KAColors.SurfaceVariant)
        assertEquals(Color(0xFF393941), KAColors.Outline)
    }

    @Test
    fun seriesListHeaderRendersAndSelectsSeason() {
        var selectedSeason = 1
        val meta = MetaDetails(id = "series_1", type = "series", name = "Test Series")
        compose.setContent {
            DetailSeriesListHeader(
                meta = meta,
                groupedEpisodes = emptyMap(),
                seasons = listOf(1, 2),
                currentSeason = selectedSeason,
                horizontalScrollPadding = 16.dp,
                onSeasonSelect = { selectedSeason = it },
                onSeasonLongPress = null,
            )
        }

        // Click dropdown trigger to expand options
        compose.onNodeWithText("1", substring = true).performClick()
        // Click season 2 in dropdown
        compose.onNodeWithText("2", substring = true).performClick()
        compose.runOnIdle { assertEquals(2, selectedSeason) }
    }

    @Test
    fun seasonOneIsDisplayedOnInitialRender() {
        val meta = MetaDetails(id = "series_1", type = "series", name = "Test Series")
        compose.setContent {
            DetailSeriesListHeader(
                meta = meta,
                groupedEpisodes = emptyMap(),
                seasons = listOf(1, 2, 3),
                currentSeason = 1,
                horizontalScrollPadding = 16.dp,
                onSeasonSelect = {},
                onSeasonLongPress = null,
            )
        }

        compose.onNodeWithText("1", substring = true).assertIsDisplayed()
    }
}
