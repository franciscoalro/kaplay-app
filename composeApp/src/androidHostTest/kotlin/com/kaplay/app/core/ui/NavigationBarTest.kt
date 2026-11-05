package com.kaplay.app.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
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
class NavigationBarTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun navigationBarRendersAllFourDestinations() {
        var selectedIndex = 0
        compose.setContent {
            KAPlayNavigationBar {
                NavItem(
                    selected = selectedIndex == 0,
                    onClick = { selectedIndex = 0 },
                    icon = Icons.Filled.Home,
                    contentDescription = "Início",
                    label = "Início",
                )
                NavItem(
                    selected = selectedIndex == 1,
                    onClick = { selectedIndex = 1 },
                    icon = Icons.Filled.Search,
                    contentDescription = "Busca",
                    label = "Busca",
                )
                NavItem(
                    selected = selectedIndex == 2,
                    onClick = { selectedIndex = 2 },
                    icon = Icons.Filled.VideoLibrary,
                    contentDescription = "Biblioteca",
                    label = "Biblioteca",
                )
                NavItem(
                    selected = selectedIndex == 3,
                    onClick = { selectedIndex = 3 },
                    icon = Icons.Filled.Person,
                    contentDescription = "Perfil",
                    label = "Perfil",
                )
            }
        }

        compose.onNodeWithText("Início").assertIsDisplayed()
        compose.onNodeWithText("Busca").assertIsDisplayed()
        compose.onNodeWithText("Biblioteca").assertIsDisplayed()
        compose.onNodeWithText("Perfil").assertIsDisplayed()

        compose.onNodeWithText("Busca").performClick()
        compose.runOnIdle { assertEquals(1, selectedIndex) }
    }

    @Test
    fun floatingNavigationBarRendersItemsThroughKAPlayNavigationBar() {
        var clicked = false
        val items = listOf(
            FloatingNavigationItem(
                label = "Início",
                selected = true,
                onClick = { clicked = true },
                icon = Icons.Filled.Home,
            ),
        )
        compose.setContent {
            FloatingNavigationBar(
                items = items,
            )
        }

        compose.onNodeWithText("Início").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(true, clicked) }
    }
}
