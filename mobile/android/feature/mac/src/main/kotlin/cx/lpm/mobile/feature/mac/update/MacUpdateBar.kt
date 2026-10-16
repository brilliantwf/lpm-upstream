package cx.lpm.mobile.feature.mac.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.CapsuleButton
import cx.lpm.mobile.core.designsystem.component.clickableNoIndication
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.BusyAgentPlace
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.feature.mac.R
import cx.lpm.mobile.feature.mac.data.MacUpdateLookup
import cx.lpm.mobile.feature.mac.data.MacUpdatePhase

/** What the bar's buttons do. */
@Immutable
class MacUpdateBarActions(
    val install: () -> Unit = {},
    val cancel: () -> Unit = {},
    val dismiss: () -> Unit = {},
    val busyPlaces: () -> List<BusyAgentPlace> = { emptyList() }
)

/** The update bar under the live Mac's projects list, wired to its ViewModel. */
@Composable
fun MacUpdateBarRoute(modifier: Modifier = Modifier) {
    val viewModel = macUpdateViewModel(SessionKey.Live)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val landed = (state.update.phase as? MacUpdatePhase.Updated)?.release
    LaunchedEffect(landed) {
        if (landed != null) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }
    MacUpdateBar(
        state = state,
        actions = MacUpdateBarActions(
            install = viewModel::install,
            cancel = viewModel::cancel,
            dismiss = viewModel::dismiss,
            busyPlaces = viewModel::busyPlaces
        ),
        modifier = modifier
    )
}

/**
 * Under the projects list: a newer lpm for the live Mac, and the install of it once
 * one runs there, whoever started it — or the answer to a check asked for from the
 * menu (iOS `MacUpdateBar`).
 */
@Composable
fun MacUpdateBar(
    state: MacUpdateUiState,
    actions: MacUpdateBarActions,
    modifier: Modifier = Modifier
) {
    // Leaving, the bar keeps what it last said rather than its empty state.
    var shown by remember { mutableStateOf(state) }
    if (state.showsBar && shown != state) shown = state
    AnimatedVisibility(
        visible = state.showsBar,
        modifier = modifier,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        BarContent(shown, actions)
    }
}

@Composable
private fun BarContent(state: MacUpdateUiState, actions: MacUpdateBarActions) {
    val colors = LpmTheme.colors
    Row(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
            .fillMaxWidth()
            .background(colors.menuBackground, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) { BarIcon(state) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = barTitle(state),
                style = LpmTheme.typography.subheadline.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BarDetail(state)
        }
        BarTrailing(state, actions)
    }
}

@Composable
private fun barTitle(state: MacUpdateUiState): String {
    val update = state.update
    when (update.lookup) {
        MacUpdateLookup.Checking -> return stringResource(R.string.mac_update_checking_for_updates)
        MacUpdateLookup.UpToDate -> return stringResource(R.string.mac_update_up_to_date_title)
        is MacUpdateLookup.Failed -> return stringResource(R.string.mac_update_check_failed)
        null -> Unit
    }
    if (update.failure != null) return stringResource(R.string.mac_update_failed)
    val target = update.newer?.let { stringResource(R.string.mac_update_lpm_release, it) }
        ?: stringResource(R.string.mac_update_lpm)
    return when (val phase = update.phase) {
        MacUpdatePhase.Checking, is MacUpdatePhase.Downloading ->
            stringResource(R.string.mac_update_updating_to, target)

        MacUpdatePhase.Installing -> stringResource(R.string.mac_update_installing_target, target)

        MacUpdatePhase.Restarting -> stringResource(R.string.mac_update_restarting)

        is MacUpdatePhase.Updated -> stringResource(
            R.string.mac_update_updated_to_lpm,
            phase.release
        )

        null -> stringResource(R.string.mac_update_available, target)
    }
}

@Composable
private fun BarDetail(state: MacUpdateUiState) {
    val update = state.update
    val error = (update.lookup as? MacUpdateLookup.Failed)?.message ?: update.failure
    val phase = update.phase
    when {
        error != null -> DetailText(error, maxLines = 2)

        phase is MacUpdatePhase.Downloading && phase.percent >= 0 -> LinearProgressIndicator(
            progress = { phase.percent / 100f },
            modifier = Modifier
                .widthIn(max = 160.dp)
                .fillMaxWidth(),
            color = LpmTheme.colors.tint,
            trackColor = LpmTheme.colors.systemFill,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {}
        )

        else -> barSubtitle(state).takeIf { it.isNotEmpty() }?.let { DetailText(it, maxLines = 1) }
    }
}

@Composable
private fun DetailText(text: String, maxLines: Int) {
    Text(
        text = text,
        style = LpmTheme.typography.caption1.copy(fontFeatureSettings = "tnum"),
        color = LpmTheme.colors.secondaryLabel,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun barSubtitle(state: MacUpdateUiState): String {
    val update = state.update
    val runs = update.version?.let { stringResource(R.string.mac_update_runs, it) }.orEmpty()
    if (update.lookup != null) return runs
    if (update.cancelling) return stringResource(R.string.mac_update_cancelling)
    return when (update.phase) {
        MacUpdatePhase.Checking -> stringResource(R.string.mac_update_checking)
        is MacUpdatePhase.Downloading -> stringResource(R.string.mac_update_downloading)
        MacUpdatePhase.Installing -> stringResource(R.string.mac_update_restarts_when_done)
        MacUpdatePhase.Restarting -> stringResource(R.string.mac_update_reconnects)
        is MacUpdatePhase.Updated -> stringResource(R.string.mac_update_reconnected)
        null -> runs
    }
}

@Composable
private fun BarIcon(state: MacUpdateUiState) {
    val update = state.update
    val colors = LpmTheme.colors
    val (icon, tint) = when {
        update.lookup == MacUpdateLookup.Checking -> null to colors.gray

        update.lookup == MacUpdateLookup.UpToDate ->
            DesignR.drawable.ic_check_circle_fill to
                colors.green

        update.lookup is MacUpdateLookup.Failed -> DesignR.drawable.ic_warning_fill to colors.orange

        update.failure != null -> DesignR.drawable.ic_warning_fill to colors.orange

        update.phase == null -> DesignR.drawable.ic_arrow_circle_down_fill to colors.tint

        update.phase is MacUpdatePhase.Updated ->
            DesignR.drawable.ic_check_circle_fill to
                colors.green

        else -> null to colors.gray
    }
    if (icon == null) {
        ActivityIndicator(size = ActivityIndicatorSize.Small)
    } else {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}

/** The bar's buttons, past a gap of at least 4 dp beside the text (`Spacer(minLength: 4)`). */
@Composable
private fun BarTrailing(
    state: MacUpdateUiState,
    actions: MacUpdateBarActions,
    modifier: Modifier = Modifier
) {
    val update = state.update
    val phase = update.phase
    if (phase == null && update.lookup == MacUpdateLookup.Checking) return
    if (phase != null && !phase.cancellable) return
    Row(
        modifier = modifier.padding(start = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            phase != null -> CapsuleButton(
                text = stringResource(R.string.mac_update_cancel),
                onClick = actions.cancel,
                enabled = !update.cancelling
            )

            update.lookup != null -> CloseButton(actions.dismiss)

            else -> {
                if (update.canInstall) {
                    MacUpdateButton(state.macLabel, actions.busyPlaces, actions.install) { start ->
                        CapsuleButton(
                            text = stringResource(
                                if (update.error == null) {
                                    R.string.mac_update_update
                                } else {
                                    R.string.mac_update_try_again
                                }
                            ),
                            onClick = start,
                            prominent = true,
                            enabled = state.canStartInstall
                        )
                    }
                }
                CloseButton(actions.dismiss)
            }
        }
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit) {
    val label = stringResource(R.string.mac_update_close)
    Box(
        Modifier
            .size(28.dp)
            .semantics { contentDescription = label }
            .clickableNoIndication(onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(DesignR.drawable.ic_close),
            contentDescription = null,
            tint = LpmTheme.colors.secondaryLabel,
            modifier = Modifier.size(16.dp)
        )
    }
}
