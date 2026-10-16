package cx.lpm.mobile.core.designsystem.code

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import cx.lpm.mobile.core.designsystem.theme.LpmColors
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * The syntax colors of iOS's `DiffHighlighter` for [colors]' appearance: Xcode-like
 * keywords, strings, numbers and YAML keys; comments in the secondary label, the rest
 * in the label.
 */
@Immutable
class SyntaxColors(colors: LpmColors) {
    private val dark = colors.isDark
    val plain: Color = colors.label
    val comment: Color = colors.secondaryLabel
    val keyword: Color = shade(0x9B2393, 0xFF7AB2)
    val text: Color = shade(0xC41A16, 0xFF8170)
    val number: Color = shade(0x1C00CF, 0x79C0FF)
    val key: Color = shade(0x0B7285, 0x4EC9B0)

    fun color(token: SyntaxToken): Color = when (token) {
        SyntaxToken.Plain -> plain
        SyntaxToken.Keyword -> keyword
        SyntaxToken.Text -> text
        SyntaxToken.Comment -> comment
        SyntaxToken.Number -> number
        SyntaxToken.Key -> key
    }

    private fun shade(light: Long, dark: Long) = Color(0xFF000000 or if (this.dark) dark else light)
}

/** [SyntaxColors] for the current theme. */
@Composable
fun rememberSyntaxColors(): SyntaxColors {
    val colors = LpmTheme.colors
    return remember(colors) { SyntaxColors(colors) }
}
