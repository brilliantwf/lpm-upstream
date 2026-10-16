package cx.lpm.mobile.feature.usage.usage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.LimitWindow
import cx.lpm.mobile.feature.usage.R

/**
 * One plan-usage window meter, the desktop `UsageMeter`: the percentage with its pace
 * verdict beside it (below it once large text leaves no room), the bar with its pace
 * tick, and when the window resets — or runs out first.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UsageMeter(
    label: String,
    win: LimitWindow?,
    windowMs: Double,
    now: Double,
    stale: Boolean,
    modifier: Modifier = Modifier
) {
    val meter = meterState(win, windowMs, now)
    val colors = LpmTheme.colors
    val type = LpmTheme.typography
    val description = stringResource(R.string.usage_meter_label, label)
    Column(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = description
                stateDescription = meter.accessibilityValue
            },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label.uppercase(),
            style = type.caption2.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
            color = colors.secondaryLabel
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                Modifier.alignByBaseline(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    if (meter.hasData) {
                        meter.shown.toString()
                    } else {
                        stringResource(
                            R.string.usage_no_value
                        )
                    },
                    style = type.title2.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = if (meter.hasData && !stale) colors.label else colors.secondaryLabel,
                    modifier = Modifier.alignByBaseline()
                )
                if (meter.hasData) {
                    Text(
                        stringResource(R.string.usage_percent),
                        style = type.subheadline,
                        color = colors.secondaryLabel,
                        modifier = Modifier.alignByBaseline()
                    )
                }
            }
            if (meter.verdict.isNotEmpty()) {
                Text(
                    meter.verdict,
                    style = type.caption1,
                    color = when (meter.verdictTone) {
                        VerdictTone.Critical -> colors.red
                        VerdictTone.Warning -> colors.orange
                        VerdictTone.Secondary -> colors.secondaryLabel
                    },
                    modifier = Modifier.alignByBaseline()
                )
            }
        }
        MeterBar(meter.barFraction, meter.tick, stale)
        if (meter.resetLine.isNotEmpty() || meter.runsOut.isNotEmpty()) {
            val style = type.caption1.copy(fontFeatureSettings = "tnum")
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (meter.resetLine.isNotEmpty()) {
                    Text(meter.resetLine, style = style, color = colors.secondaryLabel)
                }
                if (meter.runsOut.isNotEmpty()) {
                    Text(meter.runsOut, style = style, color = colors.orange)
                }
            }
        }
    }
}
