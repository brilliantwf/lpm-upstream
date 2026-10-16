package cx.lpm.mobile.feature.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.data.link.ConnectionIssue
import cx.lpm.mobile.core.data.link.IssueAction
import cx.lpm.mobile.core.data.link.MacBrowser
import cx.lpm.mobile.core.data.session.RepairReason
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.LinkButton
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** The connection feature's issue card, which the app hands to this screen. */
typealias IssueCardSlot =
    @Composable (issue: ConnectionIssue, handle: (IssueAction) -> Boolean) -> Unit

/** What the pairing screen's taps do. */
@Immutable
class PairingActions(
    val onSetUpTailscale: () -> Unit = {},
    val onUseTailscaleApp: () -> Unit = {},
    val onUseBuiltIn: () -> Unit = {},
    val onLocalOnly: () -> Unit = {},
    val onPickNearby: (MacBrowser.DiscoveredMac) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onScan: () -> Unit = {},
    val onEnterCode: () -> Unit = {},
    val onIssueAction: (IssueAction) -> Boolean = { false },
    val onWatchVideo: () -> Unit = {},
    val onTryDemo: () -> Unit = {}
)

@Composable
fun PairingScreen(
    state: PairingUiState,
    actions: PairingActions,
    issueCard: IssueCardSlot,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxSize()
            .background(LpmTheme.colors.systemGroupedBackground)
            .verticalScroll(rememberScrollState())
            .then(
                if (state.adding) {
                    Modifier
                } else {
                    Modifier.windowInsetsPadding(
                        WindowInsets.systemBars
                    )
                }
            )
            .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Header(state, actions.onWatchVideo)
        if (state.repair == null) {
            AwayFromHomeCard(
                state = state,
                onSetUp = actions.onSetUpTailscale,
                onUseApp = actions.onUseTailscaleApp,
                onUseBuiltIn = actions.onUseBuiltIn,
                onLocalOnly = actions.onLocalOnly
            )
        }
        if (state.showsWaysToPair) {
            NearbyMacs(
                state,
                onPick = actions.onPickNearby,
                onOpenSettings = actions.onOpenSettings
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WayTile(
                    icon = DesignR.drawable.ic_qr_code_scanner,
                    title = stringResource(
                        if (state.repair == null) R.string.way_scan else R.string.way_scan_instead
                    ),
                    onClick = actions.onScan,
                    modifier = Modifier.weight(1f)
                )
                WayTile(
                    icon = DesignR.drawable.ic_keyboard_alt,
                    title = stringResource(R.string.way_enter_code),
                    onClick = actions.onEnterCode,
                    modifier = Modifier.weight(1f)
                )
            }
            state.issue?.let { issue ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    issueCard(issue, actions.onIssueAction)
                    if (state.showDetails) {
                        state.checks.keys.sorted().forEach { host ->
                            Text(
                                "$host: ${state.checks[host]?.detail.orEmpty()}",
                                style = LpmTheme.typography.footnote.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = LpmTheme.colors.secondaryLabel
                            )
                        }
                    }
                }
            }
        }
        if (!state.adding) {
            LinkButton(
                text = stringResource(R.string.try_demo),
                onClick = actions.onTryDemo,
                icon = painterResource(DesignR.drawable.ic_play_circle),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun Header(state: PairingUiState, onWatchVideo: () -> Unit) {
    val colors = LpmTheme.colors
    Column(
        Modifier.padding(top = if (state.adding) 8.dp else 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text =
                state.repair?.let { stringResource(R.string.pairing_title_repair, it.displayName) }
                    ?: stringResource(
                        if (state.adding) R.string.pairing_title_adding else R.string.pairing_title
                    ),
            style = LpmTheme.typography.largeTitle.copy(fontWeight = FontWeight.Bold)
        )
        Text(subtitle(state), style = LpmTheme.typography.callout, color = colors.secondaryLabel)
        if (state.repair == null) {
            Row(
                Modifier
                    .padding(top = 4.dp)
                    .clickable(role = Role.Button, onClick = onWatchVideo),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(DesignR.drawable.ic_smart_display_fill),
                    contentDescription = null,
                    tint = colors.tint,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    stringResource(R.string.watch_video),
                    style = LpmTheme.typography.callout.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.tint
                )
                Text(
                    stringResource(R.string.watch_video_length),
                    style = LpmTheme.typography.callout.copy(fontFeatureSettings = "tnum"),
                    color = colors.secondaryLabel
                )
            }
        }
    }
}

@Composable
private fun subtitle(state: PairingUiState): String = stringResource(
    when (state.repairReason) {
        RepairReason.NotRecognized -> R.string.pairing_subtitle_not_recognized

        RepairReason.Identity -> R.string.pairing_subtitle_identity

        RepairReason.Chosen -> R.string.pairing_subtitle_chosen

        null -> if (state.showsWaysToPair) {
            R.string.pairing_subtitle_ways
        } else {
            R.string.pairing_subtitle_tailscale
        }
    }
)

@Composable
private fun WayTile(icon: Int, title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LpmTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.secondarySystemGroupedBackground)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 76.dp)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = colors.tint,
            modifier = Modifier.size(26.dp)
        )
        Text(
            title,
            style = LpmTheme.typography.subheadline.copy(fontWeight = FontWeight.Medium),
            color = colors.tint,
            textAlign = TextAlign.Center
        )
    }
}
