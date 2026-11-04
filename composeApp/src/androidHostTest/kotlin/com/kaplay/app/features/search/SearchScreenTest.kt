package com.kaplay.app.features.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.kaplay.app.core.ui.KAInputField
import com.kaplay.app.core.ui.KAShapes
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun searchFieldUsesFieldRadius() {
        assertEquals(12.dp, KAShapes.FieldRadius)
    }

    @Test
    fun inputFieldRendersWithPlaceholder() {
        compose.setContent {
            KAInputField(
                value = "",
                onValueChange = {},
                placeholder = "Buscar filmes e séries…",
            )
        }

        compose.onNodeWithText("Buscar filmes e séries…").assertIsDisplayed()
    }
}
