package cx.lpm.mobile.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Whether the [SheetPresentation] sheet around a destination may be swiped or tapped away. */
@Stable
class SheetDismissal {
    var disabled: Boolean by mutableStateOf(false)
        private set

    internal fun update(value: Boolean) {
        disabled = value
    }
}

private val LocalSheetDismissal = staticCompositionLocalOf<SheetDismissal?> { null }

/** Draws a [SheetPresentation] destination's [content] in the sheet [dismissal] controls. */
@Composable
fun SheetDismissalScope(dismissal: SheetDismissal, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSheetDismissal provides dismissal, content = content)
}

/**
 * SwiftUI's `interactiveDismissDisabled`: while [disabled], the sheet this destination
 * is drawn in stays put on a swipe down or a tap outside it.
 */
@Composable
fun InteractiveDismissDisabled(disabled: Boolean) {
    val dismissal = LocalSheetDismissal.current ?: return
    DisposableEffect(dismissal, disabled) {
        dismissal.update(disabled)
        onDispose { dismissal.update(false) }
    }
}
