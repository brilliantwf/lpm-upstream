package cx.lpm.mobile.core.web

import androidx.compose.ui.unit.Dp

/**
 * Where the content's text ends, kept current by the content and read when the
 * keyboard moves — a plain reference, so a cursor move doesn't recompose the
 * screen.
 */
class TextExtent {
    /** From the content's top to the bottom of its last line of text. */
    var bottom: Dp = Dp.Infinity

    /**
     * Whether giving the content its rows back pulls older text down from above,
     * so the text still ends at the bottom.
     */
    var fillsOnGrow: Boolean = true
}
