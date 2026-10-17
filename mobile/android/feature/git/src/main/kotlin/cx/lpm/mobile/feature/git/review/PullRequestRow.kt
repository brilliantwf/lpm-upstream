package cx.lpm.mobile.feature.git.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.ListDefaults
import cx.lpm.mobile.core.designsystem.component.PlainTextField
import cx.lpm.mobile.core.designsystem.component.clickableNoIndication
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.git.R

/** "Create Pull Request", when the Mac has the GitHub CLI: a `Label` row, its icon tinted. */
@Composable
internal fun CreatePullRequestRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val slot = ListDefaults.IconSlot
    Column(modifier) {
        PlainSeparator()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ListDefaults.RowMinHeight)
                .clickableNoIndication(onClick = onClick)
                .padding(horizontal = PlainRowInset, vertical = ListDefaults.RowVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(slot.spacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(slot.width), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(DesignR.drawable.ic_merge),
                    contentDescription = null,
                    tint = LpmTheme.colors.tint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = stringResource(R.string.git_review_create_pr),
                style = LpmTheme.typography.body
            )
        }
        PlainSeparator(Modifier.padding(start = slot.width + slot.spacing))
    }
}

/** A plain list's separator between rows, inset like the rows' content. */
@Composable
internal fun PlainSeparator(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = PlainRowInset),
        thickness = ListDefaults.SeparatorThickness,
        color = LpmTheme.colors.separator
    )
}
