package cx.lpm.mobile.feature.terminal.composer

import androidx.compose.ui.graphics.Color

/**
 * How a composer sits on its screen: the ground (a terminal's own, or the grouped
 * background of a reply box), the field's fill, the placeholder, and whether it
 * takes input at all (a reply box is off while its automation runs).
 */
internal class ComposerLook(
    val background: Color,
    val field: Color,
    val placeholder: String,
    val enabled: Boolean = true
)
