package cx.lpm.mobile.feature.usage.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.Shimmer
import cx.lpm.mobile.core.designsystem.component.SkeletonBar
import cx.lpm.mobile.core.designsystem.component.StatusDot
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.usage.R
import cx.lpm.mobile.feature.usage.ui.groupedCard
import cx.lpm.mobile.feature.usage.ui.providerMeta
import cx.lpm.mobile.feature.usage.ui.tint

/** No agent has run yet: what Stats reads, and the two tools it reads from. */
@Composable
internal fun StatsEmptyCard(modifier: Modifier = Modifier) {
    val colors = LpmTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .groupedCard()
            .padding(horizontal = 20.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(DesignR.drawable.ic_bar_chart),
            contentDescription = null,
            tint = colors.secondaryLabel,
            modifier = Modifier.size(40.dp)
        )
        Text(stringResource(R.string.stats_empty_title), style = LpmTheme.typography.headline)
        Text(
            stringResource(R.string.stats_empty_text),
            style = LpmTheme.typography.subheadline,
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center
        )
        Row(
            Modifier.padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            listOf("claude", "codex").forEach { key ->
                val meta = providerMeta(key)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusDot(meta.tint)
                    Text(
                        meta.label,
                        style = LpmTheme.typography.caption1,
                        color = colors.secondaryLabel
                    )
                }
            }
        }
    }
}

/** Gray stand-ins for the tiles and two charts while the first load runs. */
@Composable
internal fun StatsLoadingSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier.clearAndSetSemantics {},
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(2) { SkeletonTile(Modifier.weight(1f)) }
                }
            }
        }
        listOf(180.dp, 150.dp).forEach { height ->
            Shimmer {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(height)
                        .groupedCard()
                )
            }
        }
    }
}

@Composable
private fun SkeletonTile(modifier: Modifier = Modifier) {
    Shimmer(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .groupedCard(radius = 16.dp)
                .padding(14.dp)
                .heightIn(min = 84.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SkeletonBar(width = 70.dp, height = 10.dp)
            SkeletonBar(width = 96.dp, height = 22.dp)
            SkeletonBar(width = 120.dp, height = 9.dp)
        }
    }
}
