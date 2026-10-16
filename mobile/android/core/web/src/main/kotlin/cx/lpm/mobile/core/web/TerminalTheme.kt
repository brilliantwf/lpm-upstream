package cx.lpm.mobile.core.web

import androidx.compose.ui.graphics.Color

/**
 * The terminal color schemes, mirroring the desktop app's `terminal-themes.ts`
 * so a theme picked here looks the same as on the Mac. Only the four dynamic
 * colors (background / foreground / cursor / selection) vary per theme; the ANSI
 * palette is shared and lives in the web page (terminal.html).
 *
 * [Default] is the phone's own true-black OLED look (the Mac's default is a
 * lighter #1a1a1a); every other entry matches the desktop values exactly. The
 * out-of-the-box selection is [TerminalPrefs.DefaultTheme] (Claude Dark).
 */
enum class TerminalTheme(
    /** The stored value (Swift `rawValue`). */
    val id: String,
    val label: String,
    val background: String,
    val foreground: String,
    val cursor: String,
    val selection: String
) {
    Default("default", "Default", "#000000", "#cccccc", "#cccccc", "#444444"),
    OneDark("one-dark", "One Dark", "#282c34", "#abb2bf", "#528bff", "#3e4451"),
    Monokai("monokai", "Monokai", "#272822", "#f8f8f2", "#f8f8f0", "#49483e"),
    Dracula("dracula", "Dracula", "#282a36", "#f8f8f2", "#f8f8f2", "#44475a"),
    Nord("nord", "Nord", "#2e3440", "#d8dee9", "#d8dee9", "#434c5e"),
    SolarizedDark("solarized-dark", "Solarized Dark", "#002b36", "#839496", "#839496", "#073642"),
    GitHubDark("github-dark", "GitHub Dark", "#0d1117", "#c9d1d9", "#c9d1d9", "#1f2937"),
    ClaudeDark("claude-dark", "Claude Dark", "#2b2b2b", "#c8c8c8", "#c8c8c8", "#484848");

    /** The ground behind the page and the surrounding chrome, so they read as one surface. */
    val backgroundColor: Color get() = hexColor(background)

    /** For the settings preview swatch. */
    val foregroundColor: Color get() = hexColor(foreground)

    companion object {
        fun fromId(id: String?): TerminalTheme? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Keys and bounds for the phone-local terminal preferences (font size + theme),
 * stored under the same keys as on iOS.
 */
object TerminalPrefs {
    const val FONT_SIZE_KEY = "terminalFontSize"
    const val THEME_KEY = "terminalTheme"
    const val DEFAULT_FONT_SIZE = 12
    const val MIN_FONT_SIZE = 8
    const val MAX_FONT_SIZE = 24
    val DefaultTheme = TerminalTheme.ClaudeDark

    /** The stored theme, defaulting to Claude Dark; unknown values fall back too. */
    fun theme(raw: String?): TerminalTheme = TerminalTheme.fromId(raw) ?: DefaultTheme
}

/** `#rrggbb` (or `rrggbb`) as an opaque color; black on malformed input, as on iOS. */
internal fun hexColor(hex: String): Color {
    val digits = hex.removePrefix("#")
    val rgb = digits.takeIf { it.length == 6 }?.toLongOrNull(16) ?: return Color.Black
    return Color(0xFF000000 or rgb)
}
