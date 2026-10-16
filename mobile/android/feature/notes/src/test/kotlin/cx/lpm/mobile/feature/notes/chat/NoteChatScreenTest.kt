package cx.lpm.mobile.feature.notes.chat

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.NoteAttachment
import cx.lpm.mobile.core.model.NoteMessage
import cx.lpm.mobile.feature.notes.attachment.NoteAttachmentActions
import cx.lpm.mobile.feature.notes.composer.NotesComposerActions
import cx.lpm.mobile.feature.notes.composer.PendingNoteAttachment
import cx.lpm.mobile.feature.notes.data.NoteAttachmentsState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NoteChatScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private val file = NoteAttachment("h1", "spec.pdf", 245_123, "application/pdf")
    private val edited =
        NoteMessage(
            "n2",
            "c1",
            2,
            "Totals live in `lib/cart.ts`.",
            editedAt = 3,
            attachments = null
        )
    private val withFile =
        NoteMessage("n1", "c1", 1, "", editedAt = null, attachments = listOf(file))

    private fun show(state: NoteChatUiState, bytes: ByteArray? = null) = compose.setContent {
        LpmTheme {
            NoteChatScreen(
                state = state,
                refreshing = false,
                onRefresh = {},
                actions = NoteChatActions(
                    back = { events += "back" },
                    clearErrors = { events += "clear" },
                    menu = NoteMenuActions(
                        edit = { events += "edit ${it.id}" },
                        copy = { events += "copy ${it.id}" },
                        delete = { events += "delete ${it.id}" }
                    ),
                    attachments = NoteAttachmentActions(
                        bytes = { bytes },
                        fetch = { events += "fetch ${it.hash}" },
                        share = { a, _ -> events += "share ${a.hash}" }
                    )
                ),
                composer = NotesComposerActions(
                    setText = { events += "text $it" },
                    send = { events += "send" },
                    photoLibrary = { events += "photos" },
                    chooseFiles = { events += "files" },
                    remove = { events += "remove $it" }
                )
            )
        }
    }

    private fun transcript(attachments: NoteAttachmentsState = NoteAttachmentsState()) =
        NoteChatUiState(
            title = "Checkout redesign",
            items = listOf(
                NoteChatItem.Note(edited, "3:41 PM", busy = false),
                NoteChatItem.Note(withFile, "3:40 PM", busy = false),
                NoteChatItem.Day(1, "Today")
            ),
            overlay = NoteChatOverlay.None,
            attachments = attachments
        )

    @Test
    fun notesShowUnderTheirDayWithTimesAndEditedMarks() {
        show(transcript())
        compose.onNodeWithText("Checkout redesign").assertExists()
        compose.onNodeWithText("Today").assertExists()
        compose.onNodeWithText("Edited").assertExists()
        compose.onNodeWithText("3:41 PM").assertExists()
        compose.onNodeWithText("spec.pdf").assertExists()
        compose.onNodeWithText("245 KB").assertExists()
    }

    @Test
    fun aLongPressOffersEditCopyAndDelete() {
        show(transcript())
        compose.onNodeWithText("3:41 PM").performTouchInput { longClick() }
        compose.onNodeWithText("Copy").performClick()
        compose.onNodeWithText("3:41 PM").performTouchInput { longClick() }
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithText("3:41 PM").performTouchInput { longClick() }
        compose.onNodeWithText("Delete").performClick()
        assertEquals(listOf("copy n2", "edit n2", "delete n2"), events)
    }

    @Test
    fun aFileDownloadsThenShares() {
        show(transcript())
        compose.onNodeWithContentDescription("Download").performClick()
        assertEquals(listOf("fetch h1"), events)
    }

    @Test
    fun aDownloadedFileShares() {
        show(transcript(NoteAttachmentsState(cached = setOf("h1"))), bytes = byteArrayOf(1))
        compose.onNodeWithContentDescription("Share").performClick()
        assertEquals(listOf("share h1"), events)
    }

    @Test
    fun aFileTooLargeSaysSoInPlace() {
        show(
            transcript(
                NoteAttachmentsState(
                    error = mapOf("h1" to "Couldn't open the attachment. Try again.")
                )
            )
        )
        compose.onNodeWithText("Couldn't open the attachment. Try again.").assertExists()
        compose.onNodeWithContentDescription("Download").assertDoesNotExist()
    }

    @Test
    fun sendWaitsForSomethingToSend() {
        show(NoteChatUiState(title = "Ideas", overlay = NoteChatOverlay.Empty))
        compose.onNodeWithText("No notes here yet").assertExists()
        compose.onNodeWithText("Write the first note in this chat below.").assertExists()
        compose.onNodeWithContentDescription("Send").assertIsNotEnabled()
        compose.onNodeWithText("Write a note…").performTextInput("Gift notes")
        assertEquals(listOf("text Gift notes"), events)
    }

    @Test
    fun queuedFilesShowAsChipsAndTheAttachMenuOffersPickers() {
        val draft = NoteDraft(
            text = "with files",
            pending = listOf(
                PendingNoteAttachment(1, "photo.jpg", "image/jpeg"),
                PendingNoteAttachment(2, "notes.txt", "text/plain", data = byteArrayOf(1))
            )
        )
        show(NoteChatUiState(title = "Ideas", overlay = NoteChatOverlay.None, draft = draft))
        compose.onNodeWithText("Preparing…").assertExists()
        compose.onNodeWithText("notes.txt").assertExists()
        compose.onNodeWithContentDescription("Send").assertIsNotEnabled()
        compose.onAllNodes(hasContentDescription("Remove"))[0].performClick()
        compose.onNodeWithContentDescription("Attach").performClick()
        compose.onNodeWithText("Photo Library").assertExists()
        compose.onNodeWithText("Choose Files").performClick()
        assertEquals(listOf("remove 1", "files"), events)
    }

    @Test
    fun aReadyDraftSends() {
        show(
            NoteChatUiState(
                title = "Ideas",
                overlay = NoteChatOverlay.None,
                draft = NoteDraft(text = "hi")
            )
        )
        compose.onNodeWithContentDescription("Send").assertIsEnabled().performClick()
        assertEquals(listOf("send"), events)
    }

    @Test
    fun anErrorShowsInTheBannerUntilClosed() {
        show(transcript().copy(error = "Sending timed out. Check your Mac and try again."))
        compose.onNodeWithText("Sending timed out. Check your Mac and try again.").assertExists()
        compose.onNodeWithContentDescription("Close").performClick()
        assertEquals(listOf("clear"), events)
    }
}
