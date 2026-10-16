package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * A row that opens a nested menu ("Git ›"): picking it shows [content] in the same
 * panel under a header that leads back to the menu it was picked from.
 */
@Composable
fun ContextMenuSubmenu(
    title: String,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    iconDescription: String? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val controller = LocalContextMenuController.current
    MenuRow(
        title = title,
        onClick = { controller?.open(Submenu(title, icon, iconDescription, subtitle, content)) },
        modifier = modifier,
        icon = icon,
        iconDescription = iconDescription,
        subtitle = subtitle,
        trailing = {
            Glyph(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                size = 22.dp,
                bounds = DpSize(6.dp, 10.2.dp),
                tint = LpmTheme.colors.label
            )
        }
    )
}

@Composable
internal fun SubmenuHeader(submenu: Submenu, onBack: () -> Unit, modifier: Modifier = Modifier) {
    MenuRow(
        title = submenu.title,
        onClick = onBack,
        modifier = modifier,
        icon = submenu.icon,
        iconDescription = submenu.iconDescription,
        subtitle = submenu.subtitle,
        emphasized = true,
        trailing = {
            Glyph(
                painter = painterResource(R.drawable.ic_keyboard_arrow_down),
                contentDescription = null,
                size = 22.dp,
                bounds = DpSize(10.2.dp, 6.dp),
                tint = LpmTheme.colors.label
            )
        }
    )
}
