package cx.lpm.mobile.feature.usage.usage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.LinkButton
import cx.lpm.mobile.core.designsystem.component.Shimmer
import cx.lpm.mobile.core.designsystem.component.SkeletonBar
import cx.lpm.mobile.core.designsystem.component.StatusDot
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.USAGE_GENERIC_ERROR
import cx.lpm.mobile.feature.usage.R
import cx.lpm.mobile.feature.usage.ui.groupedCard
import cx.lpm.mobile.feature.usage.ui.providerMeta
import cx.lpm.mobile.feature.usage.ui.tint

/**
 * Placeholder for a tool with nothing to show yet — dashed rather than solid so it
 * never reads as a card whose meters happen to be empty.
 */
@Composable
internal fun UsageEmptyPanel(
    provider: String,
    name: String,
    dim: Boolean,
    text: String,
    modifier: Modifier = Modifier
) {
    val colors = LpmTheme.colors
    val border = colors.separator
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val width = 1.dp.toPx()
                drawRoundRect(
                    color = border,
                    topLeft = Offset(width / 2, width / 2),
                    size = Size(size.width - width, size.height - width),
                    cornerRadius = CornerRadius(20.dp.toPx() - width / 2),
                    style = Stroke(
                        width = width,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(5.dp.toPx(), 4.dp.toPx())
                        )
                    )
                )
            }
            .padding(16.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(providerMeta(provider).tint, Modifier.alpha(if (dim) 0.35f else 0.6f))
            Text(
                name,
                style = LpmTheme.typography.subheadline.copy(fontWeight = FontWeight.SemiBold),
                color = colors.secondaryLabel
            )
        }
        Text(text, style = LpmTheme.typography.footnote, color = colors.secondaryLabel)
    }
}

/**
 * Shown above cards still on screen when a refresh fails: without it the dim simply
 * lifts and hours-old percentages read as current.
 */
@Composable
internal fun UsageRefreshBanner(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LpmTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.orange.copy(alpha = 0.12f), shape)
            .border(1.dp, colors.orange.copy(alpha = 0.3f), shape)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            painter = painterResource(DesignR.drawable.ic_warning_fill),
            contentDescription = null,
            tint = colors.orange,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(16.dp)
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.usage_refresh_failed),
                style = LpmTheme.typography.footnote,
                color = colors.label
            )
            if (message != USAGE_GENERIC_ERROR && message.isNotEmpty()) {
                Text(message, style = LpmTheme.typography.caption1, color = colors.secondaryLabel)
            }
            LinkButton(
                text = stringResource(R.string.try_again),
                onClick = onRetry,
                textStyle = LpmTheme.typography.footnote.copy(fontWeight = FontWeight.Medium)
            )
        }
    }
}

/** Two card-shaped stand-ins while the first reading loads. */
@Composable
internal fun UsageSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier.clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(2) {
            Shimmer {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .groupedCard()
                        .padding(16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusDot(LpmTheme.colors.tertiarySystemFill)
                        SkeletonBar(width = 84.dp, height = 12.dp)
                        Spacer(Modifier.weight(1f))
                        SkeletonBar(width = 72.dp, height = 10.dp)
                    }
                    HorizontalDivider(
                        Modifier.padding(vertical = 12.dp),
                        thickness = Dp.Hairline,
                        color = LpmTheme.colors.separator
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        repeat(2) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SkeletonBar(width = 52.dp, height = 9.dp)
                                SkeletonBar(width = 74.dp, height = 22.dp)
                                SkeletonBar(width = null, height = 6.dp)
                                SkeletonBar(width = 168.dp, height = 10.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}
