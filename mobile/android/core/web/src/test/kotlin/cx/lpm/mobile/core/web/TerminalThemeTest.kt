package cx.lpm.mobile.core.web

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalThemeTest {
    @Test
    fun matchesTheIosTable() {
        val rows = TerminalTheme.entries.map {
            listOf(it.id, it.label, it.background, it.foreground, it.cursor, it.selection)
        }
        assertEquals(
            listOf(
                listOf("default", "Default", "#000000", "#cccccc", "#cccccc", "#444444"),
                listOf("one-dark", "One Dark", "#282c34", "#abb2bf", "#528bff", "#3e4451"),
                listOf("monokai", "Monokai", "#272822", "#f8f8f2", "#f8f8f0", "#49483e"),
                listOf("dracula", "Dracula", "#282a36", "#f8f8f2", "#f8f8f2", "#44475a"),
                listOf("nord", "Nord", "#2e3440", "#d8dee9", "#d8dee9", "#434c5e"),
                listOf(
                    "solarized-dark",
                    "Solarized Dark",
                    "#002b36",
                    "#839496",
                    "#839496",
                    "#073642"
                ),
                listOf("github-dark", "GitHub Dark", "#0d1117", "#c9d1d9", "#c9d1d9", "#1f2937"),
                listOf("claude-dark", "Claude Dark", "#2b2b2b", "#c8c8c8", "#c8c8c8", "#484848")
            ),
            rows
        )
    }

    @Test
    fun prefsMatchIos() {
        assertEquals("terminalFontSize", TerminalPrefs.FONT_SIZE_KEY)
        assertEquals("terminalTheme", TerminalPrefs.THEME_KEY)
        assertEquals(12, TerminalPrefs.DEFAULT_FONT_SIZE)
        assertEquals(8, TerminalPrefs.MIN_FONT_SIZE)
        assertEquals(24, TerminalPrefs.MAX_FONT_SIZE)
        assertEquals(TerminalTheme.ClaudeDark, TerminalPrefs.DefaultTheme)
    }

    @Test
    fun storedValuesFallBackToClaudeDark() {
        assertEquals(TerminalTheme.Nord, TerminalPrefs.theme("nord"))
        assertEquals(TerminalTheme.ClaudeDark, TerminalPrefs.theme("solarized-light"))
        assertEquals(TerminalTheme.ClaudeDark, TerminalPrefs.theme(null))
    }

    @Test
    fun colorsParseFromHex() {
        assertEquals(Color(0xFF2B2B2B), TerminalTheme.ClaudeDark.backgroundColor)
        assertEquals(Color(0xFFC9D1D9), TerminalTheme.GitHubDark.foregroundColor)
        assertEquals(Color(0xFF002B36), hexColor("002b36"))
        assertEquals(Color.Black, hexColor("#12345"))
        assertEquals(Color.Black, hexColor("#zzzzzz"))
    }
}
