package com.kaplay.app.features.details

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.kaplay.app.features.details.components.DetailSeriesContent
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DetailEpisodeCardTriggersTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun episodeCardThreeDotsButtonTriggersEpisodeActions() {
        var actionEpisode: MetaVideo? = null
        val testEpisode = MetaVideo(
            id = "tomato:6945:37521",
            title = "E01 · Maomao",
            season = 1,
            episode = 1,
            runtime = 23,
        )
        val meta = MetaDetails(
            id = "kusuriya",
            type = "series",
            name = "The Apothecary Diaries",
            videos = listOf(testEpisode),
        )

        compose.setContent {
            DetailSeriesContent(
                meta = meta,
                horizontalScrollPadding = 16.dp,
                onEpisodeClick = {},
                onEpisodeLongPress = { actionEpisode = it },
            )
        }

        compose.onNodeWithContentDescription("Ações do episódio").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(testEpisode.id, actionEpisode?.id)
        }
    }
}
