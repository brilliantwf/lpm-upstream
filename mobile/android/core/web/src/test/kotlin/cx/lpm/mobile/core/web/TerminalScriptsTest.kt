package cx.lpm.mobile.core.web

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalScriptsTest {
    private fun base64Argument(script: String, function: String): String {
        val prefix = "window.$function('"
        assert(script.startsWith(prefix) && script.endsWith("')")) { script }
        return String(
            Base64.getDecoder().decode(script.removePrefix(prefix).removeSuffix("')")),
            Charsets.UTF_8
        )
    }

    @Test
    fun outputCrossesAsBase64OfUtf8() {
        val output = "\u001B[1;36m~/dev/storefront\u001B[0m \u001B[2m$\u001B[0m " +
            "⏺ 'quoted' \\ 😀\r\n"
        assertEquals(output, base64Argument(TerminalScripts.feed(output.toByteArray()), "lpmFeed"))
        assertEquals(output, base64Argument(TerminalScripts.seed(output), "lpmSeed"))
        val composed = "fix the build\nplease"
        assertEquals(composed, base64Argument(TerminalScripts.submit(composed), "lpmSubmit"))
    }

    @Test
    fun callsThePageFunctionsIosCalls() {
        assertEquals("window.lpmSetInsets(0, 291)", TerminalScripts.setInsets(0, 291))
        assertEquals("window.lpmSetFontSize(14)", TerminalScripts.setFontSize(14))
        assertEquals(
            "window.lpmSetTheme('#2b2b2b','#c8c8c8','#c8c8c8','#484848')",
            TerminalScripts.setTheme(TerminalTheme.ClaudeDark)
        )
        assertEquals(
            "window.lpmSetTheme('#282c34','#abb2bf','#528bff','#3e4451')",
            TerminalScripts.setTheme(TerminalTheme.OneDark)
        )
        assertEquals("window.lpmCapture(200)", TerminalScripts.capture(200))
        assertEquals("window.lpmRevive()", TerminalScripts.REVIVE)
    }

    @Test
    fun captureResultsAreJsonStrings() {
        assertEquals("line 1\nline \"2\"", TerminalScripts.captured("\"line 1\\nline \\\"2\\\"\""))
        assertEquals("", TerminalScripts.captured("null"))
        assertEquals("", TerminalScripts.captured(null))
        assertEquals("", TerminalScripts.captured("42"))
        assertEquals("", TerminalScripts.captured("{"))
    }
}
