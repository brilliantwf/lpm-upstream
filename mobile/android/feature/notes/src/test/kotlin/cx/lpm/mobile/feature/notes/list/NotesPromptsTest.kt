package cx.lpm.mobile.feature.notes.list

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.NoteChat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotesPromptsTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()
    private val chat = NoteChat("c1", "Ideas", 1, 1)

    private fun show(prompt: NotesPrompt) = compose.setContent {
        LpmTheme {
            NotesPrompts(
                prompt = prompt,
                onCreate = { events += "create $it" },
                onRename = { c, title -> events += "rename ${c.id} $title" },
                onDelete = { events += "delete ${it.id}" },
                onDismiss = { events += "dismiss" }
            )
        }
    }

    @Test
    fun aNewChatIsNamedThenCreated() {
        show(NotesPrompt.Create)
        compose.onNodeWithText("New chat").assertExists()
        compose.onNodeWithText("Title").performTextInput("Launch plan")
        compose.onNodeWithText("Create").performClick()
        assertEquals(listOf("create Launch plan", "dismiss"), events)
    }

    @Test
    fun renamingStartsFromTheCurrentTitle() {
        show(NotesPrompt.Rename(chat))
        compose.onNodeWithText("Rename chat").assertExists()
        compose.onNodeWithText("Ideas").performTextReplacement("Backlog")
        compose.onNodeWithText("Save").performClick()
        assertEquals(listOf("rename c1 Backlog", "dismiss"), events)
    }

    @Test
    fun deletingAsksFirst() {
        show(NotesPrompt.Delete(chat))
        compose.onNodeWithText("Delete chat?").assertExists()
        compose.onNodeWithText(
            "“Ideas” and every note in it will be removed. This can't be undone."
        ).assertExists()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(listOf("delete c1", "dismiss"), events)
    }
}
