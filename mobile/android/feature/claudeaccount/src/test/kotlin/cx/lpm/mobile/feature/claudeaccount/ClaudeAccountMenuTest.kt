package cx.lpm.mobile.feature.claudeaccount

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import cx.lpm.mobile.core.designsystem.component.ContextMenu
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.ClaudeAccountChoice
import cx.lpm.mobile.core.model.ClaudeAccounts
import cx.lpm.mobile.core.model.ClaudeOwnChoice
import cx.lpm.mobile.core.model.Project
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClaudeAccountMenuTest {
    @get:Rule
    val compose = createComposeRule()

    private val picks = mutableListOf<ClaudeAccountChoice>()
    private val lists = mutableListOf<List<String>>()

    private val accounts = ClaudeAccounts.parse(
        Json.parseToJsonElement(
            """
            {"accounts": [
              {"id": "default", "signedIn": true, "email": "you@example.com", "plan": "max",
               "confirmed": true},
              {"id": "work", "label": "Work", "signedIn": true, "plan": "max", "confirmed": true},
              {"id": "personal", "label": "Personal", "signedIn": true, "plan": "pro"},
              {"id": "client", "label": "Client", "signedIn": true, "plan": "team"},
              {"id": "laptop", "label": "Old laptop", "signedIn": false}
            ],
            "switching": {"enabled": true, "active": true, "maxMembers": 3, "pools": [
              {"project": null, "members": ["default", "work"], "current": "default"},
              {"project": "mobile-app", "members": ["work", "default"], "current": "work"}
            ]}}
            """
        ).jsonObject
    )

    private fun project(json: String) = Project.parse(Json.parseToJsonElement(json).jsonObject)

    private val storefront = project("""{"name": "storefront"}""")

    private fun show(
        project: Project = storefront,
        accounts: ClaudeAccounts? = this.accounts,
        parentChoice: ClaudeOwnChoice = ClaudeOwnChoice()
    ) = compose.setContent {
        LpmTheme {
            Box {
                ContextMenu(expanded = true, onDismissRequest = {}) {
                    ClaudeAccountMenu(
                        state = ClaudeAccountMenuUiState(
                            accounts = accounts,
                            project = project,
                            parentChoice = parentChoice,
                            usage = mapOf(
                                "default" to "5h 71% · 7d 28%",
                                "work" to "5h 34% · 7d 11%"
                            )
                        ),
                        onPick = { picks += it },
                        onSetList = { lists += it }
                    )
                }
            }
        }
    }

    private fun checked(title: String) = hasText(title) and hasContentDescription("selected")

    @Test
    fun hiddenUntilTheMacHasAnAccountBeyondTheMainLogin() {
        val mainOnly = ClaudeAccounts(accounts.accounts.take(1), accounts.switching)
        show(accounts = mainOnly)
        compose.onNodeWithText("Claude Account").assertDoesNotExist()
    }

    @Test
    fun theChoicesReadLikeIos() {
        show()
        compose.onNodeWithText("Claude Account").performClick()
        compose.onNode(checked("Main accounts")).assertExists()
        // A checked choice shows its title alone, as on iOS: not "Main login" under it.
        compose.onNode(hasText("Main accounts") and hasText("Main login")).assertDoesNotExist()
        listOf(
            "Always use one account", "5h 71% · 7d 28%", "Work", "5h 34% · 7d 11%", "Personal",
            "Client", "Old laptop", "Not signed in", "Take turns on up to 3, in order"
        ).forEach { compose.onNodeWithText(it).performScrollTo().assertExists() }
        compose.onNodeWithText("Same as parent").assertDoesNotExist()

        compose.onNodeWithText("Work").performScrollTo().performClick()
        assertEquals(listOf<ClaudeAccountChoice>(ClaudeAccountChoice.Pin("work")), picks)
    }

    @Test
    fun theMainLoginIsPinnedAsEmpty() {
        show()
        compose.onNodeWithText("Claude Account").performClick()
        compose.onNodeWithText("5h 71% · 7d 28%").performClick()
        assertEquals(listOf<ClaudeAccountChoice>(ClaudeAccountChoice.Pin("")), picks)
    }

    @Test
    fun aCopyCanFollowItsParent() {
        show(
            project = project("""{"name": "storefront-2", "parentName": "storefront"}"""),
            parentChoice = ClaudeOwnChoice(account = "work")
        )
        compose.onNodeWithText("Claude Account").performClick()
        compose.onNode(checked("Same as parent")).assertExists()
        compose.onNodeWithText("Main accounts").performClick()
        assertEquals(listOf<ClaudeAccountChoice>(ClaudeAccountChoice.Main), picks)
    }

    @Test
    fun theListTakesTurnsInOrderAndBlocksWhatCantJoin() {
        show(project = project("""{"name": "mobile-app", "claudeAccounts": ["work"]}"""))
        compose.onNodeWithText("Claude Account").performClick()
        compose.onNodeWithText("Choose Accounts").performScrollTo().performClick()
        compose.onNodeWithText("Take turns, in this order").assertExists()
        compose.onNode(hasText("Work") and hasClickAction()).assertIsNotEnabled()
        compose.onNode(hasText("Client") and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithText("Allow it in lpm on your Mac first").assertExists()
        compose.onNodeWithText("Sign in on your Mac first").performScrollTo().assertExists()
        compose.onNode(hasText("Personal") and hasClickAction()).assertIsEnabled().performClick()
        assertEquals(listOf(listOf("work", "personal")), lists)
    }

    @Test
    fun aFullListTakesNoMore() {
        show(
            project = project(
                """{"name": "mobile-app", "claudeAccounts": ["work", "default", "personal"]}"""
            )
        )
        compose.onNodeWithText("Claude Account").performClick()
        compose.onNodeWithText("Choose Accounts").performScrollTo().performClick()
        compose.onNode(hasText("Work") and hasClickAction()).assertIsEnabled().performClick()
        assertEquals(listOf(listOf("default", "personal")), lists)
    }

    @Test
    fun theListLeadsBackToTheAccountMenu() {
        show()
        compose.onNodeWithText("Claude Account").performClick()
        compose.onNodeWithText("Choose Accounts").performScrollTo().performClick()
        compose.onNodeWithText("Always use one account").assertDoesNotExist()
        compose.onNodeWithText("Choose Accounts").performClick()
        compose.onNodeWithText("Always use one account").assertExists()
    }

    @Test
    fun consentNamesTheAccountsAndConfirmsThem() {
        var confirmed = false
        val personal = accounts.account("personal")!!
        compose.setContent {
            LpmTheme {
                ClaudeConsentPrompt(
                    request = ClaudeConsentRequest(
                        "storefront",
                        listOf("personal"),
                        listOf(personal)
                    ),
                    onConfirm = { confirmed = true },
                    onDismiss = {}
                )
            }
        }
        compose.onNodeWithText("Add this account to the list?").assertExists()
        compose.onNodeWithText("Personal\n\nNew Claude sessions", substring = true).assertExists()
        compose.onNodeWithText("Cancel").assertExists()
        compose.onNodeWithText("It's Only Mine").performClick()
        assertEquals(true, confirmed)
    }

    @Test
    fun consentForSeveralAccountsSpeaksOfThem() {
        val two = listOf(accounts.account("personal")!!, accounts.account("default")!!)
        compose.setContent {
            LpmTheme {
                ClaudeConsentPrompt(
                    request = ClaudeConsentRequest("storefront", two.map { it.id }, two),
                    onConfirm = {},
                    onDismiss = {}
                )
            }
        }
        compose.onNodeWithText("Add these accounts to the list?").assertExists()
        compose.onNodeWithText("They're Only Mine").assertExists()
        compose.onNodeWithText("Personal, Main login (you@example.com)", substring = true)
            .assertExists()
    }
}
