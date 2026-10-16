package cx.lpm.mobile.feature.files.preview

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.files.fetch.FileFetch
import cx.lpm.mobile.feature.files.fetch.FilePreviewKind
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FilePreviewSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private fun ready(name: String, kind: FilePreviewKind, truncated: Boolean = false) =
        FileFetch.Phase.Ready(File("/tmp/f1/$name"), kind, truncated)

    private fun show(state: FilePreviewUiState, line: Int = 0) = compose.setContent {
        LpmTheme {
            val actions = FilePreviewActions(
                onMarkdownSource = { events += "source $it" },
                markdown = { text, modifier -> Text("rendered: $text", modifier) },
                document = { file, modifier ->
                    NoPreview(file, "phone", { events += "share ${it.name}" }, modifier)
                },
                media = { file, modifier -> Text("playing ${file.name}", modifier) }
            )
            FilePreviewSheet(
                state = state,
                onDone = { events += "done" },
                onShare = { events += "share ${it.name}" },
                onRetry = { events += "retry" }
            ) { FilePreviewContent(it, state, line, actions, Modifier) }
        }
    }

    @Test
    fun loadingShowsTheNameAndPathThenTheBytesSoFar() {
        show(FilePreviewUiState("app/checkout/page.tsx", received = 524_288, total = 1_048_576))
        compose.onNodeWithText("app/checkout/page.tsx").assertExists()
        compose.onNodeWithText("524 KB of 1 MB").assertExists()
        compose.onNodeWithContentDescription("Share…").assertDoesNotExist()
        compose.onNodeWithText("Done").performClick()
        assertEquals(listOf("done"), events)
    }

    @Test
    fun aFailedCopyExplainsAndRetries() {
        show(FilePreviewUiState("a.txt", phase = FileFetch.Phase.Failed("No such file")))
        compose.onNodeWithText("Couldn't open the file").assertExists()
        compose.onNodeWithText("No such file").assertExists()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(listOf("retry"), events)
    }

    @Test
    fun markdownRendersAndSwitchesToItsSource() {
        val phase = ready("README.md", FilePreviewKind.Markdown)
        show(FilePreviewUiState("README.md", phase = phase, text = "# Hi"))
        compose.onNodeWithText("rendered: # Hi").assertExists()
        compose.onNodeWithText("Preview").assertIsSelected()
        compose.onNodeWithText("Source").performClick()
        compose.onNodeWithContentDescription("Share…").performClick()
        assertEquals(listOf("source true", "share README.md"), events)
    }

    @Test
    fun markdownSourceIsHighlightedText() {
        val phase = ready("README.md", FilePreviewKind.Markdown)
        show(FilePreviewUiState("README.md", phase = phase, text = "# Hi", markdownSource = true))
        compose.onNodeWithText("# Hi").assertExists()
        compose.onNodeWithText("Source").assertIsSelected()
    }

    @Test
    fun theHeadOfALongTextSaysSoAndCantBeShared() {
        val phase = ready("big.log", FilePreviewKind.Text, truncated = true)
        show(FilePreviewUiState("logs/big.log", phase = phase, text = "line one\nline two"))
        compose.onNodeWithText("line two").assertExists()
        compose.onNodeWithText("Showing the first 1 MB — this file is larger.").assertExists()
        compose.onNodeWithContentDescription("Share…").assertDoesNotExist()
    }

    @Test
    fun anEmptyFileSaysSo() {
        show(FilePreviewUiState("a.txt", phase = ready("a.txt", FilePreviewKind.Text), text = ""))
        compose.onNodeWithText("Empty file").assertExists()
    }

    @Test
    fun aTappedLineIsScrolledIntoView() {
        val text = (1..400).joinToString("\n") { "line $it" }
        show(
            FilePreviewUiState("a.txt", phase = ready("a.txt", FilePreviewKind.Text), text = text),
            line = 300
        )
        compose.waitForIdle()
        compose.onNodeWithText("line 300").assertExists()
        compose.onNodeWithText("line 1").assertDoesNotExist()
    }

    @Test
    fun aDocumentThePhoneCantShowCanBeShared() {
        show(FilePreviewUiState("deck.key", phase = ready("deck.key", FilePreviewKind.Document)))
        compose.onNodeWithText("No preview").assertExists()
        compose.onNodeWithText(
            "Phone can't show this kind of file. Share it to open it in another app."
        ).assertExists()
        compose.onNodeWithText("Share").performClick()
        assertEquals(listOf("share deck.key"), events)
    }

    @Test
    fun mediaPlays() {
        show(FilePreviewUiState("clip.mp4", phase = ready("clip.mp4", FilePreviewKind.Media)))
        compose.onNodeWithText("playing clip.mp4").assertExists()
    }
}
