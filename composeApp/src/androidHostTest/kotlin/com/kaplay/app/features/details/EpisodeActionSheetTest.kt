package com.kaplay.app.features.details

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kaplay.app.features.details.components.EpisodeWatchedActionSheet
import com.kaplay.app.features.watchprogress.WatchProgressEntry
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpisodeActionSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val testEpisode = MetaVideo(
        id = "tomato:6945:37521",
        title = "E01 · Maomao",
        season = 1,
        episode = 1,
        runtime = 23,
        thumbnail = "https://cdn.example/thumb.jpg",
    )

    @Test
    fun episodeSheetRendersRichHeaderAndPlayAction() {
        var playFromBeginningClicked = false
        compose.setContent {
            EpisodeWatchedActionSheet(
                episode = testEpisode,
                seasonLabel = "Temporada 1",
                isEpisodeWatched = false,
                canMarkPreviousEpisodes = false,
                arePreviousEpisodesWatched = false,
                isSeasonWatched = false,
                onDismiss = {},
                onPlayFromBeginning = { playFromBeginningClicked = true },
                onToggleWatched = {},
                onTogglePreviousWatched = {},
                onToggleSeasonWatched = {},
            )
        }

        compose.onNodeWithText("E01 · Maomao").assertIsDisplayed()
        compose.onNodeWithText("Reproduzir").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(true, playFromBeginningClicked) }
    }

    @Test
    fun resumeActionAppearsOnlyWhenProgressExists() {
        val progress = WatchProgressEntry(
            videoId = "tomato:6945:37521",
            durationMs = 1_380_000L,
            positionMs = 300_000L,
            lastWatchedAt = 1000L,
        )

        compose.setContent {
            EpisodeWatchedActionSheet(
                episode = testEpisode,
                seasonLabel = "Temporada 1",
                isEpisodeWatched = false,
                canMarkPreviousEpisodes = false,
                arePreviousEpisodesWatched = false,
                isSeasonWatched = false,
                progressEntry = progress,
                onDismiss = {},
                onToggleWatched = {},
                onTogglePreviousWatched = {},
                onToggleSeasonWatched = {},
            )
        }

        compose.onNodeWithText("Continuar de onde parei").assertIsDisplayed()
    }
}
