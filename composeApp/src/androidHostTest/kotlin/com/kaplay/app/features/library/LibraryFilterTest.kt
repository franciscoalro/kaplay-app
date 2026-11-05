package com.kaplay.app.features.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryFilterTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun libraryCategoryFiltersRenderAndSwitchSelection() {
        var selectedCategory = LibraryCategoryFilter.FAVORITES
        compose.setContent {
            LibraryCategoryFilterRow(
                selectedCategory = selectedCategory,
                onCategorySelected = { selectedCategory = it },
            )
        }

        compose.onNodeWithText("Favoritos").assertIsDisplayed()
        compose.onNodeWithText("Em andamento").assertIsDisplayed()
        compose.onNodeWithText("Assistidos").assertIsDisplayed()

        compose.onNodeWithText("Em andamento").performClick()
        compose.runOnIdle {
            assertEquals(LibraryCategoryFilter.IN_PROGRESS, selectedCategory)
        }

        compose.onNodeWithText("Assistidos").performClick()
        compose.runOnIdle {
            assertEquals(LibraryCategoryFilter.WATCHED, selectedCategory)
        }
    }
}
