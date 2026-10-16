package cx.lpm.mobile.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * The gray `chevron.right` that ends a row which opens another screen; a Button row
 * tints it ([tint]).
 */
@Composable
fun DisclosureChevron(modifier: Modifier = Modifier, tint: Color = LpmTheme.colors.tertiaryLabel) {
    Glyph(
        painter = painterResource(R.drawable.ic_chevron_right),
        contentDescription = null,
        size = 24.dp,
        bounds = DpSize(6.6.dp, 11.3.dp),
        tint = tint,
        modifier = modifier
    )
}
