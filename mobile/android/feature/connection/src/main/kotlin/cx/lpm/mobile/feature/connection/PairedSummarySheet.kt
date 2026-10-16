package cx.lpm.mobile.feature.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.lpm.mobile.core.data.link.PairedSummary
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.CapsuleButton
import cx.lpm.mobile.core.designsystem.component.ModalSheet
import cx.lpm.mobile.core.designsystem.component.PrimaryButton
import cx.lpm.mobile.core.designsystem.component.rememberModalSheetState
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.navigation.SheetStack
import cx.lpm.mobile.core.navigation.rememberSheetNavigator
import cx.lpm.mobile.core.ui.NotificationAccess
import cx.lpm.mobile.core.ui.openNotificationSettings
import cx.lpm.mobile.core.ui.rememberNotificationAccess

private enum class SummaryPage { Summary, Tailscale }

/** "Connected to …": shown once a pairing lands, with what's left to set up. */
@Composable
internal fun PairedSummarySheet(summary: PairedSummary, onDismiss: () -> Unit) {
    val sheet = rememberModalSheetState()
    val navigator = rememberSheetNavigator(SummaryPage.Summary)
    ModalSheet(onDismissRequest = onDismiss, state = sheet) {
        SheetStack(navigator) { page ->
            when (page) {
                SummaryPage.Summary -> {
                    val viewModel =
                        hiltViewModel<PairedSummaryViewModel, PairedSummaryViewModel.Factory>(
                            key = "summary-${summary.id}"
                        ) { it.create(summary.macId) }
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    SheetPage(title = "") {
                        PairedSummaryContent(
                            state = state,
                            onSetUp = { navigator.push(SummaryPage.Tailscale) },
                            onDone = { sheet.dismiss(onDismiss) }
                        )
                    }
                }

                SummaryPage.Tailscale -> TailnetSettingsPage(onBack = navigator::back)
            }
        }
    }
}

@Composable
internal fun PairedSummaryContent(
    state: PairedSummaryUiState,
    onSetUp: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LpmTheme.colors
    val context = LocalContext.current
    val (notifications, allow) = rememberNotificationAccess()
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painterResource(DesignR.drawable.ic_check_circle_fill),
                contentDescription = null,
                tint = colors.green,
                modifier = Modifier.size(56.dp)
            )
            val name = state.record?.displayName ?: stringResource(R.string.your_mac)
            Text(
                stringResource(R.string.summary_title, name),
                style = LpmTheme.typography.title2.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
        }
        Column(
            Modifier.background(colors.secondarySystemGroupedBackground, RoundedCornerShape(18.dp))
        ) {
            AwayRow(state.away, onSetUp)
            RowDivider()
            val tailnet = state.overTailscale
            SummaryRow(
                icon = if (tailnet) DesignR.drawable.ic_language else DesignR.drawable.ic_wifi,
                tint = colors.gray,
                title = stringResource(
                    if (tailnet) R.string.summary_over_tailscale else R.string.summary_on_wifi
                )
            ) {
                StatusWord(stringResource(R.string.summary_works))
            }
            RowDivider()
            SummaryRow(
                icon = DesignR.drawable.ic_notifications_unread_fill,
                tint = colors.red,
                title = stringResource(R.string.summary_notifications),
                detail = stringResource(R.string.summary_notifications_detail)
            ) {
                when (notifications) {
                    NotificationAccess.NotDetermined ->
                        SmallButton(stringResource(R.string.summary_allow), allow, prominent = true)

                    NotificationAccess.Denied ->
                        SmallButton(
                            stringResource(R.string.settings),
                            context::openNotificationSettings
                        )

                    NotificationAccess.Allowed -> StatusWord(stringResource(R.string.summary_on))
                }
            }
        }
        PrimaryButton(
            stringResource(R.string.done),
            onDone,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.heightIn(min = 64.dp)
        )
    }
}

@Composable
private fun AwayRow(away: AwayState, onSetUp: () -> Unit) {
    val colors = LpmTheme.colors
    val title = stringResource(R.string.summary_away)
    val icon = DesignR.drawable.ic_language
    when (away) {
        AwayState.Ready, AwayState.App -> SummaryRow(
            icon,
            colors.blue,
            title,
            stringResource(
                if (away ==
                    AwayState.Ready
                ) {
                    R.string.summary_away_ready
                } else {
                    R.string.summary_away_app
                }
            )
        ) { StatusWord(stringResource(R.string.summary_ready)) }

        AwayState.TurnOnHere, AwayState.FinishHere -> SummaryRow(
            icon,
            colors.blue,
            title,
            stringResource(
                if (away == AwayState.FinishHere) {
                    R.string.summary_away_finish
                } else {
                    R.string.summary_away_turn_on
                }
            )
        ) {
            SmallButton(
                stringResource(
                    if (away ==
                        AwayState.FinishHere
                    ) {
                        R.string.summary_finish
                    } else {
                        R.string.summary_set_up
                    }
                ),
                onClick = onSetUp
            )
        }

        AwayState.MacNotSetUp -> SummaryRow(
            icon,
            colors.gray,
            title,
            stringResource(R.string.summary_away_mac)
        ) {
            SmallButton(stringResource(R.string.summary_set_up), onClick = onSetUp)
        }
    }
}

@Composable
private fun SummaryRow(
    icon: Int,
    tint: Color,
    title: String,
    detail: String? = null,
    trailing: @Composable RowScope.() -> Unit
) {
    val colors = LpmTheme.colors
    Row(
        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(30.dp)
                .background(tint, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = LpmTheme.typography.body.copy(fontWeight = FontWeight.Medium))
            detail?.let {
                Text(it, style = LpmTheme.typography.footnote, color = colors.secondaryLabel)
            }
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
private fun RowDivider() {
    Box(
        Modifier
            .padding(start = 58.dp)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(LpmTheme.colors.separator)
    )
}

@Composable
private fun StatusWord(text: String) {
    Text(
        text,
        style = LpmTheme.typography.subheadline.copy(fontWeight = FontWeight.SemiBold),
        color = LpmTheme.colors.green
    )
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit, prominent: Boolean = false) {
    CapsuleButton(
        text = text,
        onClick = onClick,
        prominent = prominent,
        textStyle = LpmTheme.typography.subheadline.copy(fontWeight = FontWeight.SemiBold)
    )
}
