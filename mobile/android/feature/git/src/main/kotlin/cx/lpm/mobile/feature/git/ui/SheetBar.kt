package cx.lpm.mobile.feature.git.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.LpmTopBar

/** A sheet's navigation bar: the title between its leading and trailing buttons. */
@Composable
internal fun SheetBar(
    title: String,
    leading: @Composable RowScope.() -> Unit,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    LpmTopBar(
        title = title,
        modifier = Modifier.padding(top = 16.dp),
        windowInsets = WindowInsets(0),
        leading = leading,
        trailing = trailing
    )
}

/** A small spinner where a bar button goes while its work runs. */
@Composable
internal fun BarSpinner(modifier: Modifier = Modifier) {
    Box(modifier.size(44.dp), contentAlignment = Alignment.Center) {
        ActivityIndicator(size = ActivityIndicatorSize.Small)
    }
}
