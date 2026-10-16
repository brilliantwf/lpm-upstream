package cx.lpm.mobile.feature.usage.rows

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.component.OutlinedTag
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.usage.R
import cx.lpm.mobile.feature.usage.ui.usagePalette

/** The desktop sidebar's "in use" / "pool" tag beside a Claude account's name. */
@Composable
internal fun UsageAccountTag(mark: UsageAccountMark, modifier: Modifier = Modifier) {
    when (mark) {
        UsageAccountMark.Current -> Text(
            stringResource(R.string.rows_in_use),
            style = LpmTheme.typography.caption1.copy(fontWeight = FontWeight.SemiBold),
            color = usagePalette.inUseText,
            maxLines = 1,
            modifier = modifier
                .background(usagePalette.inUseWash, RoundedCornerShape(5.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp)
        )

        UsageAccountMark.Standby -> OutlinedTag(stringResource(R.string.rows_pool), modifier)
    }
}
