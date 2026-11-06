package com.kaplay.app.features.details

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import com.kaplay.app.features.details.components.DetailSeriesListEpisode
import com.kaplay.app.features.details.components.KASeasonDropdown
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DetailEpisodeListCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val testEpisode = MetaVideo(
        id = "tomato:6945:37521",
        title = "E01 · Maomao",
        season = 1,
        episode = 1,
        runtime = 23,
    )
    private val testMeta = MetaDetails(
        id = "kusuriya",
        type = "series",
        name = "The Apothecary Diaries",
        videos = listOf(testEpisode),
    )

    @Test
    fun seasonDropdownPreservesLongPressAction() {
        var longPressed = false
        compose.setContent {
            KASeasonDropdown(
                seasons = listOf(1, 2),
                selectedSeason = 1,
                onSeasonSelected = {},
                onLongPress = { longPressed = true },
            )
        }

        compose.onNodeWithText("Season 1").performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(true, longPressed) }
    }

    @Test
    fun episodeListCardRendersTitleAndOptionsButton() {
        var clicked = false
        var moreClicked = false
        compose.setContent {
            DetailSeriesListEpisode(
                meta = testMeta,
                episode = testEpisode,
                progressByVideoId = emptyMap(),
                watchedKeys = emptySet(),
                episodeRatings = emptyMap(),
                episodeRatingsVisibility = EpisodeRatingsVisibility.SHOW_ALL,
                blurUnwatchedEpisodes = false,
                onEpisodeClick = { clicked = true },
                onEpisodeLongPress = { moreClicked = true },
            )
        }

        compose.onNodeWithText("E01 · Maomao").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(true, clicked) }

        compose.onNodeWithContentDescription("Opções do episódio").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(true, moreClicked) }
    }
}
