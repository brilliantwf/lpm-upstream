package cx.lpm.mobile.feature.projects.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.data.projects.DuplicateProgress
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.projects.R

/** A bottom HUD while a duplicate batch runs, counting the copies as they land. */
@Composable
fun DuplicateProgressBar(progress: DuplicateProgress, modifier: Modifier = Modifier) {
    val colors = LpmTheme.colors
    Row(
        modifier = modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
            .fillMaxWidth()
            .background(colors.menuBackground, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActivityIndicator(size = ActivityIndicatorSize.Small)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.duplicating, progress.source),
                style = LpmTheme.typography.subheadline.copy(fontWeight = FontWeight.Medium),
                maxLines = 1
            )
            Text(
                text = if (progress.total > 0) {
                    stringResource(R.string.duplicating_progress, progress.done, progress.total)
                } else {
                    stringResource(R.string.duplicating_working)
                },
                style = LpmTheme.typography.caption1.copy(fontFeatureSettings = "tnum"),
                color = colors.secondaryLabel
            )
        }
        if (progress.total > 1) {
            LinearProgressIndicator(
                progress = { progress.done.toFloat() / progress.total },
                modifier = Modifier.width(64.dp),
                color = colors.tint,
                trackColor = colors.systemFill,
                strokeCap = StrokeCap.Round,
                drawStopIndicator = {}
            )
        }
    }
}
