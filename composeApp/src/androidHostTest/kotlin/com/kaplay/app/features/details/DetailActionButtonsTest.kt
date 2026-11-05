package com.kaplay.app.features.details

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.kaplay.app.core.ui.KAColors
import com.kaplay.app.core.ui.KAShapes
import com.kaplay.app.features.details.components.DetailActionButtons
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DetailActionButtonsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun buttonTokensMatchDesignSystemSpec() {
        assertEquals(10.dp, KAShapes.ButtonRadius)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF9729D7), KAColors.Primary)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF23232B), KAColors.SurfaceVariant)
    }

    @Test
    fun playButtonIsClickableAndRendersLabel() {
        var playClicked = false
        compose.setContent {
            DetailActionButtons(
                playLabel = "Assistir",
                playEnabled = true,
                onPlayClick = { playClicked = true },
            )
        }

        compose.onNodeWithText("Assistir").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(true, playClicked) }
    }
}
