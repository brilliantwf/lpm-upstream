package cx.lpm.mobile.feature.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.LinkButton
import cx.lpm.mobile.core.designsystem.component.PrimaryButton
import cx.lpm.mobile.core.designsystem.component.SecondaryButton
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * The first thing on the pairing screen: lpm reaches a Mac away from its Wi-Fi only
 * through Tailscale, so setting that up comes before pairing. People who already run
 * the Tailscale app say so and move on; pairing for this Wi-Fi only is a choice
 * made knowingly.
 */
@Composable
internal fun AwayFromHomeCard(
    state: PairingUiState,
    onSetUp: () -> Unit,
    onUseApp: () -> Unit,
    onUseBuiltIn: () -> Unit,
    onLocalOnly: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LpmTheme.colors
    val phase = state.awayPhase
    val (icon, tint) = when (phase) {
        AwayPhase.Ready, AwayPhase.App -> DesignR.drawable.ic_check to colors.green
        AwayPhase.LocalOnly -> DesignR.drawable.ic_wifi to colors.orange
        AwayPhase.InProgress, AwayPhase.NotSetUp -> DesignR.drawable.ic_language to colors.blue
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.secondarySystemGroupedBackground, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier
                    .size(42.dp)
                    .background(tint, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(awayTitle(phase)), style = LpmTheme.typography.headline)
                Text(
                    awayDetail(state),
                    style = LpmTheme.typography.subheadline,
                    color = colors.secondaryLabel
                )
            }
        }
        when (phase) {
            AwayPhase.NotSetUp -> {
                PrimaryButton(stringResource(R.string.away_set_up), onSetUp)
                LinkButton(
                    stringResource(R.string.away_use_app),
                    onUseApp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                LocalOnlyButton(onLocalOnly)
            }

            AwayPhase.InProgress -> {
                val finish = state.tailscale.state == "needsLogin"
                SecondaryButton(
                    stringResource(
                        if (finish) R.string.away_finish_sign_in else R.string.away_open
                    ),
                    onSetUp
                )
                LocalOnlyButton(onLocalOnly)
            }

            AwayPhase.LocalOnly -> SecondaryButton(stringResource(R.string.away_set_up), onSetUp)

            AwayPhase.App -> LinkButton(stringResource(R.string.away_use_built_in), onUseBuiltIn)

            AwayPhase.Ready -> Unit
        }
    }
}

@Composable
private fun LocalOnlyButton(onLocalOnly: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        LinkButton(
            stringResource(R.string.away_local_only),
            onLocalOnly,
            color = LpmTheme.colors.secondaryLabel
        )
    }
}

private fun awayTitle(phase: AwayPhase): Int = when (phase) {
    AwayPhase.Ready -> R.string.away_title_ready
    AwayPhase.App -> R.string.away_title_app
    AwayPhase.InProgress -> R.string.away_title_in_progress
    AwayPhase.NotSetUp -> R.string.away_title_not_set_up
    AwayPhase.LocalOnly -> R.string.away_title_local_only
}

@Composable
private fun awayDetail(state: PairingUiState): String = when (state.awayPhase) {
    AwayPhase.Ready -> stringResource(R.string.away_detail_ready)

    AwayPhase.App -> stringResource(R.string.away_detail_app)

    AwayPhase.NotSetUp -> stringResource(R.string.away_detail_not_set_up)

    AwayPhase.LocalOnly -> stringResource(R.string.away_detail_local_only)

    AwayPhase.InProgress -> when (state.tailscale.state) {
        "needsLogin" -> stringResource(R.string.away_detail_needs_login)
        "needsApproval" -> stringResource(R.string.away_detail_needs_approval)
        "error", "stopped" -> state.tailscale.error ?: stringResource(R.string.away_detail_broken)
        else -> stringResource(R.string.away_detail_connecting)
    }
}
