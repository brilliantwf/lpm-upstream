package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cx.lpm.mobile.core.designsystem.R
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContextMenuTest {
    @get:Rule
    val compose = createComposeRule()

    private val picked = mutableListOf<String>()

    private fun setMenu() {
        compose.setContent {
            LpmTheme {
                var open by remember { mutableStateOf(false) }
                Box {
                    GlassIconButton(
                        icon = painterResource(R.drawable.ic_more_horiz),
                        contentDescription = "More",
                        onClick = { open = true }
                    )
                    ContextMenu(expanded = open, onDismissRequest = { open = false }) {
                        ContextMenuItem(title = "Stop", onClick = { picked += "Stop" })
                        ContextMenuSubmenu(title = "Git") {
                            ContextMenuItem(title = "Pull", onClick = { picked += "Pull" })
                        }
                        ContextMenuDivider()
                        ContextMenuItem(title = "Rename…", onClick = { picked += "Rename…" })
                    }
                }
            }
        }
    }

    @Test
    fun opensFromItsAnchorAndClosesOnPick() {
        setMenu()
        compose.onNodeWithText("Stop").assertDoesNotExist()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Stop").assertExists()
        compose.onNodeWithText("Rename…").performClick()
        compose.onNodeWithText("Stop").assertDoesNotExist()
        assertEquals(listOf("Rename…"), picked)
    }

    @Test
    fun submenuShowsItsItemsAndLeadsBack() {
        setMenu()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Git").performClick()
        compose.onNodeWithText("Pull").assertExists()
        compose.onNodeWithText("Stop").assertDoesNotExist()
        compose.onNodeWithText("Git").performClick()
        compose.onNodeWithText("Stop").assertExists()
        compose.onNodeWithText("Git").performClick()
        compose.onNodeWithText("Pull").performClick()
        compose.onNodeWithText("Pull").assertDoesNotExist()
        assertEquals(listOf("Pull"), picked)
    }

    @Test
    fun aNestedSubmenuLeadsBackOneLevelAtATime() {
        compose.setContent {
            LpmTheme {
                Box {
                    ContextMenu(expanded = true, onDismissRequest = {}) {
                        ContextMenuItem(title = "Stop", onClick = {})
                        ContextMenuSubmenu(title = "Claude Account") {
                            ContextMenuSectionHeader("Always use one account")
                            ContextMenuSubmenu(
                                title = "Choose Accounts",
                                subtitle = "Take turns on up to 3, in order"
                            ) {
                                ContextMenuItem(
                                    title = "Work",
                                    onClick = { picked += "Work" },
                                    icon = painterResource(R.drawable.ic_check),
                                    iconDescription = "selected"
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Claude Account").performClick()
        compose.onNodeWithText("Choose Accounts").performClick()
        compose.onNodeWithText("Always use one account").assertDoesNotExist()
        compose.onNodeWithText("Take turns on up to 3, in order").assertExists()
        compose.onNode(hasText("Work") and hasContentDescription("selected"))
            .assertExists()

        compose.onNodeWithText("Choose Accounts").performClick()
        compose.onNodeWithText("Always use one account").assertExists()
        compose.onNodeWithText("Stop").assertDoesNotExist()
        compose.onNodeWithText("Claude Account").performClick()
        compose.onNodeWithText("Stop").assertExists()
    }

    @Test
    fun pickerRowSelectsFromItsMenu() {
        var theme = "System"
        compose.setContent {
            LpmTheme {
                var selected by remember { mutableStateOf("System") }
                PickerRow(
                    title = "Theme",
                    options = listOf("System", "Light", "Dark"),
                    selected = selected,
                    onSelect = {
                        selected = it
                        theme = it
                    },
                    optionLabel = { it }
                )
            }
        }
        compose.onNodeWithText("Theme").performClick()
        compose.onNodeWithText("Dark").performClick()
        compose.onNodeWithText("Light").assertDoesNotExist()
        compose.onNodeWithText("Dark").assertExists()
        assertEquals("Dark", theme)
    }
}
