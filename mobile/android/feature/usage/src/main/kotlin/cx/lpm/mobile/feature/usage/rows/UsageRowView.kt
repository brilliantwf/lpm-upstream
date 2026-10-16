package cx.lpm.mobile.feature.usage.rows

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ContextMenu
import cx.lpm.mobile.core.designsystem.component.ContextMenuItem
import cx.lpm.mobile.core.designsystem.component.ListDefaults
import cx.lpm.mobile.core.designsystem.component.StatusDot
import cx.lpm.mobile.core.designsystem.component.listSeparator
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.ClaudeInUse
import cx.lpm.mobile.feature.usage.R
import cx.lpm.mobile.feature.usage.ui.providerMeta
import cx.lpm.mobile.feature.usage.ui.tint
import cx.lpm.mobile.feature.usage.ui.usagePalette
import cx.lpm.mobile.feature.usage.usage.UsageLanes
import cx.lpm.mobile.feature.usage.usage.UsagePaceBar

/**
 * One usage row, as the desktop sidebar draws it: the tool's dot, the account's name
 * and tag, when its window comes back and how much of it is gone, over a two-lane bar.
 * Tapping opens what the desktop shows on hover; on a project's screen a long press
 * offers switching the project to the account.
 */
@Composable
internal fun UsageRowView(
    item: UsageRowItem,
    inUse: ClaudeInUse?,
    expanded: Boolean,
    now: Double,
    macNow: Double,
    onToggle: () -> Unit,
    onSwitch: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val row = item.row
    // The account a project's screen can switch to: a Claude account row, on a project
    // whose sessions run on this Mac.
    val switchTarget = if (onSwitch != null && inUse?.project != null) row.account else null
    var menuOpen by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val label = accessibilityLabel(row, item.mark)
    val value = accessibilityValue(row)
    val hint = stringResource(if (expanded) R.string.rows_hint_hide else R.string.rows_hint_show)
    Box(
        modifier
            .listSeparator()
            .fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = ListDefaults.RowPadding, vertical = RowInset)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics {
                        contentDescription = label
                        stateDescription = value
                    }
                    .combinedClickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = hint,
                        onLongClick = if (switchTarget != null) ({ menuOpen = true }) else null,
                        onClick = onToggle
                    )
                    .alpha(if (pressed) 0.5f else 1f)
                    .padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                RowHeader(row, item.mark, expanded)
                RowBar(row)
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(EXPAND_MS)) + expandVertically(tween(EXPAND_MS)),
                exit = fadeOut(tween(EXPAND_MS)) + shrinkVertically(tween(EXPAND_MS))
            ) {
                UsageRowDetail(
                    row = row,
                    inUse = inUse,
                    switchTarget = switchTarget,
                    now = now,
                    macNow = macNow,
                    onSwitch = onSwitch,
                    modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                )
            }
        }
        if (switchTarget != null && onSwitch != null) {
            SwitchMenu(
                expanded = menuOpen,
                onDismiss = { menuOpen = false },
                row = row,
                inUse = inUse,
                account = switchTarget,
                onSwitch = onSwitch
            )
        }
    }
}

@Composable
private fun RowHeader(row: UsageRow, mark: UsageAccountMark?, expanded: Boolean) {
    val colors = LpmTheme.colors
    val type = LpmTheme.typography
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(
            providerMeta(row.provider).tint,
            Modifier.alpha(if (row.stale) 0.5f else 1f)
        )
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                row.label,
                style = type.subheadline.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (mark != null) UsageAccountTag(mark)
        }
        Spacer(Modifier.width(4.dp))
        val detailStyle = type.footnote.copy(fontFeatureSettings = "tnum")
        if (row.windowShort.isNotEmpty()) {
            Text(row.windowShort, style = detailStyle, color = colors.secondaryLabel)
        }
        Text(row.detail, style = detailStyle, color = colors.secondaryLabel, maxLines = 1)
        if (row.percentText.isNotEmpty()) {
            Text(
                row.percentText,
                style = type.subheadline.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum"
                ),
                color = if (row.stale) colors.secondaryLabel else colors.label,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 40.dp)
            )
        }
        Box(Modifier.width(12.dp), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(
                    if (expanded) {
                        DesignR.drawable.ic_keyboard_arrow_down
                    } else {
                        DesignR.drawable.ic_chevron_right
                    }
                ),
                contentDescription = null,
                tint = colors.tertiaryLabel,
                modifier = Modifier.requiredSize(ChevronSize)
            )
        }
    }
}

@Composable
private fun RowBar(row: UsageRow) {
    val fill = if (row.windowed) usagePalette.fill else providerMeta(row.provider).tint
    val lane = row.lane
    if (lane != null) {
        UsageLanes(
            upper = UsageLane(row.fraction),
            upperFill = fill,
            pace = row.pace,
            lower = lane,
            dim = row.stale
        )
    } else {
        UsagePaceBar(fraction = row.fraction, fill = fill, pace = row.pace, dim = row.stale)
    }
}

@Composable
private fun SwitchMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    row: UsageRow,
    inUse: ClaudeInUse?,
    account: String,
    onSwitch: (String) -> Unit
) {
    val project = inUse?.project ?: return
    val current = inUse.current == account
    ContextMenu(expanded = expanded, onDismissRequest = onDismiss) {
        ContextMenuItem(
            title = stringResource(R.string.rows_switch_to, row.label),
            subtitle = stringResource(R.string.rows_new_sessions_in, project.label),
            icon = painterResource(
                if (current) DesignR.drawable.ic_check else DesignR.drawable.ic_account_circle
            ),
            enabled = !(inUse.pinned && current),
            onClick = { onSwitch(account) }
        )
    }
}

@Composable
private fun accessibilityLabel(row: UsageRow, mark: UsageAccountMark?): String {
    val tag = when (mark) {
        UsageAccountMark.Current -> stringResource(R.string.rows_in_use)
        UsageAccountMark.Standby -> stringResource(R.string.rows_pool)
        null -> null
    }
    return listOfNotNull(row.label, tag).joinToString(", ")
}

@Composable
private fun accessibilityValue(row: UsageRow): String {
    if (row.percentText.isEmpty()) return row.detail
    val main = stringResource(R.string.rows_value, row.percentText, row.windowLabel, row.detail)
    return if (row.laneText.isEmpty()) {
        main
    } else {
        stringResource(
            R.string.rows_value_lane,
            main,
            row.laneText
        )
    }
}

/** Above and below a row's own padding, as an inset-grouped list row leaves it. */
private val RowInset = 14.dp

/** A caption-sized chevron, as `.font(.caption2.weight(.semibold))` draws it. */
private val ChevronSize = 17.dp
private const val EXPAND_MS = 250
