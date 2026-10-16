package cx.lpm.mobile.feature.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.data.link.AddressCheck
import cx.lpm.mobile.core.data.link.AddressKind
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.ListDefaults
import cx.lpm.mobile.core.designsystem.component.ListLeadingSlot
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** One saved address: what kind it is and whether it answers from here. */
@Composable
internal fun AddressStatusRow(
    host: String,
    inUse: Boolean,
    testing: Boolean,
    check: AddressCheck?,
    modifier: Modifier = Modifier
) {
    val colors = LpmTheme.colors
    val kind = AddressKind.of(host)
    ListRow(
        modifier = modifier,
        leadingSlot = ListLeadingSlot(width = 29.dp, spacing = 12.dp),
        verticalPadding = 8.dp,
        leading = {
            Box(
                Modifier
                    .size(29.dp)
                    .background(
                        if (kind == AddressKind.Tailscale) colors.blue else colors.gray,
                        RoundedCornerShape(7.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(kind.icon()),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        trailing = {
            Spacer(Modifier.width(8.dp))
            AddressResult(inUse, testing, check, showsUnchecked = true)
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = host,
                style = LpmTheme.typography.body.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis
            )
            Text(kind.label, style = LpmTheme.typography.footnote, color = colors.secondaryLabel)
        }
    }
}

/** "In use", a spinner while it's checked, or whether it answered. */
@Composable
internal fun AddressResult(
    inUse: Boolean,
    testing: Boolean,
    check: AddressCheck?,
    showsUnchecked: Boolean
) {
    val colors = LpmTheme.colors
    val style = LpmTheme.typography.subheadline
    when {
        inUse -> Text(
            stringResource(R.string.address_in_use),
            style = style.copy(fontWeight = FontWeight.SemiBold),
            color = colors.green
        )

        testing -> ActivityIndicator(size = ActivityIndicatorSize.Small)

        check != null -> Text(
            text = stringResource(
                if (check.reachable) R.string.address_answers else R.string.address_not_here
            ),
            style = style,
            color = if (check.reachable) colors.green else colors.secondaryLabel
        )

        showsUnchecked -> Text(
            stringResource(R.string.address_not_checked),
            style = style,
            color = colors.tertiaryLabel
        )
    }
}

internal val AddressRowSlot = ListLeadingSlot(width = 29.dp, spacing = 12.dp)

internal val AddressRowSeparator =
    ListDefaults.RowPadding + AddressRowSlot.width + AddressRowSlot.spacing
