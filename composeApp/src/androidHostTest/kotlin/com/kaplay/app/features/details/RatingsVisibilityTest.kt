package com.kaplay.app.features.details

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kaplay.app.core.ui.KAPlayTheme
import com.kaplay.app.features.details.components.DetailMetaInfo
import com.kaplay.app.features.details.components.DetailSeriesContent
import com.kaplay.app.features.watchprogress.buildPlaybackVideoId
import com.kaplay.app.features.details.components.DetailSeriesListEpisode
import com.kaplay.app.features.mdblist.MdbListMetadataService.PROVIDER_TMDB
import com.kaplay.app.features.watched.watchedItemKeys
import com.kaplay.app.features.watchprogress.WatchProgressEntry
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RatingsVisibilityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun overallVisibilityRespectsMdbListProvidersWithoutStandardRatingFallback() {
        val overall = mutableStateOf(true)
        val mdbActive = mutableStateOf(false)
        val externalRatings = mutableStateOf(listOf(MetaExternalRating(PROVIDER_TMDB, 65.0)))
        compose.setContent {
            KAPlayTheme {
                DetailMetaInfo(
                    meta = MetaDetails("tt1234567", "movie", "Movie", imdbRating = "7.8", externalRatings = externalRatings.value),
                    showOverallRatings = overall.value,
                    isMdbListActive = mdbActive.value,
                )
            }
        }
        compose.onNodeWithText("7.8").fetchSemanticsNode()
        compose.onNodeWithText("65").assertDoesNotExist()

        compose.runOnIdle { overall.value = false }
        compose.onNodeWithText("7.8").assertDoesNotExist()

        compose.runOnIdle { mdbActive.value = true }
        compose.onNodeWithText("65").fetchSemanticsNode()
        compose.onNodeWithText("7.8").assertDoesNotExist()

        compose.runOnIdle {
            externalRatings.value = emptyList()
            overall.value = true
        }
        compose.onNodeWithText("7.8").assertDoesNotExist()
        compose.onNodeWithText("65").assertDoesNotExist()

        compose.runOnIdle { mdbActive.value = false }
        compose.onNodeWithText("7.8").fetchSemanticsNode()
    }

    @Test
    fun episodeRatingsVisibilityPolicyMatchesWatchedState() {
        kotlin.test.assertTrue(EpisodeRatingsVisibility.SHOW_ALL.showRating(isWatched = false))
        kotlin.test.assertFalse(EpisodeRatingsVisibility.HIDE_UNWATCHED_EPISODES.showRating(isWatched = false))
        kotlin.test.assertTrue(EpisodeRatingsVisibility.HIDE_UNWATCHED_EPISODES.showRating(isWatched = true))
        kotlin.test.assertFalse(EpisodeRatingsVisibility.HIDE_EPISODES.showRating(isWatched = true))
    }
}
