package cx.lpm.mobile.core.web

import android.net.Uri
import android.text.InputType
import android.view.inputmethod.EditorInfo
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BridgeListenerTest {
    private val received = mutableListOf<PageMessage>()
    private val listener = BridgeListener { received += it }
    private val page = Uri.parse(WebPages.ORIGIN)

    @Test
    fun routesEnvelopesFromThePageToTheHandler() {
        listener.receive("""{"name":"input","body":"q"}""", page, isMainFrame = true)
        listener.receive("""{"name":"ready","body":{}}""", page, isMainFrame = true)
        assertEquals(listOf("input", "ready"), received.map { it.name })
        assertEquals(JsonPrimitive("q"), received.first().body)
    }

    @Test
    fun dropsOtherOriginsFramesAndJunk() {
        listener.receive(
            """{"name":"input","body":"q"}""",
            Uri.parse("https://example.com"),
            isMainFrame = true
        )
        listener.receive(
            """{"name":"input","body":"q"}""",
            Uri.parse("http://${WebPages.HOST}"),
            isMainFrame = true
        )
        listener.receive(
            """{"name":"input","body":"q"}""",
            Uri.parse("https://${WebPages.HOST}:8443"),
            isMainFrame = true
        )
        listener.receive("""{"name":"input","body":"q"}""", page, isMainFrame = false)
        listener.receive("not json", page, isMainFrame = true)
        listener.receive(null, page, isMainFrame = true)
        assertTrue(received.isEmpty())
    }

    @Test
    fun onlyTheBundledPagesAreNavigable() {
        assertTrue(WebPages.isPage(Uri.parse(WebPages.TERMINAL_URL)))
        assertTrue(WebPages.isPage(Uri.parse(WebPages.MARKDOWN_URL)))
        assertEquals(
            "https://appassets.androidplatform.net/assets/web/terminal.html",
            WebPages.TERMINAL_URL
        )
        assertEquals(
            "https://appassets.androidplatform.net/assets/web/markdown/markdown.html",
            WebPages.MARKDOWN_URL
        )
        assertFalse(WebPages.isPage(Uri.parse("https://${WebPages.HOST}/assets/other.html")))
        assertFalse(WebPages.isPage(Uri.parse("https://lpm.cx/assets/web/terminal.html")))
        assertFalse(WebPages.isPage(Uri.parse("file:///android_asset/web/terminal.html")))
    }

    @Test
    fun theKeyboardSendsKeyEventsWithoutAFullScreenEditor() {
        val attrs = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_NONE
        }
        TerminalIme.configure(attrs)
        assertEquals(InputType.TYPE_NULL, attrs.inputType)
        assertEquals(
            EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_FULLSCREEN or
                EditorInfo.IME_FLAG_NO_EXTRACT_UI,
            attrs.imeOptions
        )
    }
}
