package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.Painter

/**
 * What an open [ContextMenu] lets its rows do: close it, or open a submenu over the
 * current one (a submenu can hold submenus of its own).
 */
internal class ContextMenuController(val dismiss: () -> Unit) {
    /** The submenus opened from the panel, innermost last. */
    val submenus = mutableStateListOf<Submenu>()

    val submenu: Submenu? get() = submenus.lastOrNull()

    fun open(submenu: Submenu) {
        submenus += submenu
    }

    /** Back to the menu the innermost submenu was opened from. */
    fun back() {
        submenus.removeLastOrNull()
    }
}

internal class Submenu(
    val title: String,
    val icon: Painter?,
    val iconDescription: String?,
    val subtitle: String?,
    val items: @Composable ColumnScope.() -> Unit
)

internal val LocalContextMenuController = staticCompositionLocalOf<ContextMenuController?> { null }
