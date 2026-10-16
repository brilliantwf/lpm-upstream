package cx.lpm.mobile.feature.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.GlassCapsuleButton
import cx.lpm.mobile.core.designsystem.component.GlassIconButton
import cx.lpm.mobile.core.designsystem.component.LpmTopBar
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * One page of a sheet's `NavigationStack`: the inline title, a back button on a
 * pushed page ([onBack]), and the page's own toolbar buttons ([trailing]).
 */
@Composable
internal fun SheetPage(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxSize().background(LpmTheme.colors.systemGroupedBackground)) {
        LpmTopBar(
            title = title,
            windowInsets = WindowInsets(0),
            modifier = Modifier.padding(top = 16.dp),
            leading = onBack?.let { back ->
                {
                    GlassIconButton(
                        icon = painterResource(DesignR.drawable.ic_arrow_back_ios_new),
                        contentDescription = stringResource(R.string.back),
                        onClick = back
                    )
                }
            },
            trailing = trailing
        )
        content()
    }
}

/** The "Done" a sheet's root page closes it with. */
@Composable
internal fun DoneButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ToolbarTextButton(stringResource(R.string.done), onClick, modifier, bold = true)
}

/** A text button in the toolbar's glass capsule ("Save", "Edit"). */
@Composable
internal fun ToolbarTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bold: Boolean = false
) {
    val typography = LpmTheme.typography
    GlassCapsuleButton(
        text = text,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        textStyle = if (bold) {
            typography.headline
        } else {
            typography.body.copy(
                fontWeight = FontWeight.Normal
            )
        }
    )
}

/** A `Form`/`List` under a sheet's bar: scrolling sections, inset like iOS. */
@Composable
internal fun SheetForm(
    modifier: Modifier = Modifier,
    top: Dp = FormTopWithoutHeader,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(top = top, bottom = 24.dp),
        content = content
    )
}

/** Above a first section with no header iOS leaves more room than above one with a header. */
internal val FormTopWithoutHeader = 36.dp
internal val FormTopWithHeader = 19.dp
