package com.kaplay.app.features.details

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.kaplay.app.core.ui.KAColors
import com.kaplay.app.core.ui.KAShapes
import com.kaplay.app.features.details.components.DetailHeroMetaBadge
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DetailMetaInfoTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun metaBadgeTokensMatchSpec() {
        assertEquals(8.dp, KAShapes.ChipRadius)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF393941), KAColors.Outline)
    }

    @Test
    fun metaBadgeRendersBadgeText() {
        compose.setContent {
            DetailHeroMetaBadge(text = "16+")
        }

        compose.onNodeWithText("16+").assertIsDisplayed()
    }
}
