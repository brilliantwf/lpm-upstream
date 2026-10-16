package cx.lpm.mobile.feature.git.auto

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.ListDefaults
import cx.lpm.mobile.core.designsystem.component.listSeparator
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.GitAutoStep

/** One step of a run: its status glyph, label, and what it's doing or produced. */
@Composable
internal fun GitAutoStepRow(step: GitAutoStep, modifier: Modifier = Modifier) {
    val colors = LpmTheme.colors
    val dimmed =
        step.status == GitAutoStep.Status.Pending || step.status == GitAutoStep.Status.Skipped
    Row(
        modifier = modifier
            .listSeparator(startInset = ListDefaults.RowPadding + STEP_ICON_WIDTH + 12.dp)
            .fillMaxWidth()
            .padding(horizontal = ListDefaults.RowPadding, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(Modifier.width(STEP_ICON_WIDTH).height(22.dp), contentAlignment = Alignment.Center) {
            StepIcon(step.status)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = step.label,
                style = LpmTheme.typography.body,
                color = if (dimmed) colors.secondaryLabel else colors.label
            )
            if (step.detail.isNotEmpty() && step.status != GitAutoStep.Status.Failed) {
                val footnote = LpmTheme.typography.footnote
                Text(
                    text = step.detail,
                    style = if (step.status == GitAutoStep.Status.Done) {
                        footnote.copy(fontFamily = FontFamily.Monospace)
                    } else {
                        footnote
                    },
                    color = colors.secondaryLabel,
                    maxLines = 2,
                    overflow = TextOverflow.MiddleEllipsis
                )
            }
        }
    }
}

@Composable
private fun StepIcon(status: GitAutoStep.Status) {
    val colors = LpmTheme.colors
    val (icon, tint) = when (status) {
        GitAutoStep.Status.Running -> return ActivityIndicator(size = ActivityIndicatorSize.Small)
        GitAutoStep.Status.Done -> DesignR.drawable.ic_check_circle_fill to colors.green
        GitAutoStep.Status.Failed -> DesignR.drawable.ic_cancel_fill to colors.red
        GitAutoStep.Status.Skipped -> DesignR.drawable.ic_do_not_disturb_on to colors.tertiaryLabel
        GitAutoStep.Status.Pending -> DesignR.drawable.ic_circle to colors.tertiaryLabel
    }
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(22.dp)
    )
}

private val STEP_ICON_WIDTH = 20.dp
