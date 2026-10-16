package cx.lpm.mobile.feature.git.diff

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import cx.lpm.mobile.core.designsystem.code.SyntaxColors
import cx.lpm.mobile.core.designsystem.code.SyntaxToken
import cx.lpm.mobile.core.designsystem.theme.LpmColors
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** One color per appearance, as iOS's dynamic `UIColor(light:dark:)`. */
internal class Dynamic(val light: Long, val dark: Long) {
    fun resolve(dark: Boolean): Color = Color(0xFF000000 or if (dark) this.dark else light)
}

/**
 * GitHub-style opaque pastel diff colors (iOS `DiffPalette`): row bands behind the
 * code, a deeper gutter, and word-change tints over the row band.
 */
internal object DiffPalette {
    val addWord = Dynamic(0xABF2BC, 0x2A6435)
    val delWord = Dynamic(0xFDB8C0, 0x78353B)
    val addRow = Dynamic(0xE6FFEC, 0x12261E)
    val delRow = Dynamic(0xFFEBE9, 0x25171C)
    val addGutter = Dynamic(0xCCFFD8, 0x03351A)
    val delGutter = Dynamic(0xFFD7D5, 0x4A1418)
    val contextGutter = Dynamic(0xF6F8FA, 0x161B22)
}

/** [DiffPalette] and the syntax colors, resolved for the current appearance. */
@Immutable
class DiffColors(colors: LpmColors) {
    private val dark = colors.isDark
    private val syntax = SyntaxColors(colors)
    val label: Color = colors.label
    val gutterText: Color = colors.secondaryLabel
    val addWord = DiffPalette.addWord.resolve(dark)
    val delWord = DiffPalette.delWord.resolve(dark)
    private val addRow = DiffPalette.addRow.resolve(dark)
    private val delRow = DiffPalette.delRow.resolve(dark)
    private val addGutter = DiffPalette.addGutter.resolve(dark)
    private val delGutter = DiffPalette.delGutter.resolve(dark)
    private val contextGutter = DiffPalette.contextGutter.resolve(dark)

    fun row(kind: DiffLineKind): Color? = when (kind) {
        DiffLineKind.Added -> addRow
        DiffLineKind.Removed -> delRow
        else -> null
    }

    fun gutter(kind: DiffLineKind): Color = when (kind) {
        DiffLineKind.Added -> addGutter
        DiffLineKind.Removed -> delGutter
        else -> contextGutter
    }

    fun token(token: SyntaxToken): Color = syntax.color(token)
}

@Composable
internal fun rememberDiffColors(): DiffColors {
    val colors = LpmTheme.colors
    return remember(colors) { DiffColors(colors) }
}
