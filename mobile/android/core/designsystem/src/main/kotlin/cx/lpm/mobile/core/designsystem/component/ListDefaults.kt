package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Where a row's leading icon or dot sits: its column width and the gap to the title. */
@Immutable
data class ListLeadingSlot(val width: Dp, val spacing: Dp)

/** Spacing a section leaves below itself when it has no footer (`listSectionSpacing`). */
enum class ListSectionSpacing { Regular, Compact }

/** Metrics of an iOS 26 inset-grouped list. */
object ListDefaults {
    val CardCornerRadius = 26.dp
    val CardShape = RoundedCornerShape(CardCornerRadius)

    /** From the screen edge to the card. */
    val Margin = 16.dp

    /** From the card edge to a row's content. */
    val RowPadding = 16.dp
    val RowMinHeight = 52.dp

    /** Above and below a row's content; one line of text stays at [RowMinHeight]. */
    val RowVerticalPadding = 12.dp
    val SeparatorThickness = 1.dp

    /** An SF Symbol beside the title (`Label` in a list). */
    val IconSlot = ListLeadingSlot(width = 24.dp, spacing = 17.dp)

    /** A status dot beside the title, as in the projects list. */
    val DotSlot = ListLeadingSlot(width = 14.dp, spacing = 8.dp)

    /** Above the first section, below the top bar. */
    val ContentTopPadding = 19.dp

    /** Above a first section with no header: iOS keeps an empty header's room there. */
    val HeaderlessContentTopPadding = ContentTopPadding + 18.dp
}
