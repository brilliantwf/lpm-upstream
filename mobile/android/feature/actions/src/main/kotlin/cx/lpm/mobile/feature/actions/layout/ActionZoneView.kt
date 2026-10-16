package cx.lpm.mobile.feature.actions.layout

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.component.ContextMenu
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.Zone
import cx.lpm.mobile.feature.actions.R
import cx.lpm.mobile.feature.actions.button.ActionCallbacks
import cx.lpm.mobile.feature.actions.button.ActionFace
import cx.lpm.mobile.feature.actions.button.ActionMenuItems
import cx.lpm.mobile.feature.actions.button.Chevron
import cx.lpm.mobile.feature.actions.button.DesktopActionButton
import cx.lpm.mobile.feature.actions.button.FaceLabel
import kotlin.math.abs

/**
 * A zone as the desktop frames it (iOS `ActionZoneView`): its buttons in `rows` rows,
 * filled top to bottom and then across, inside one bordered frame. A zone with layers
 * shows one at a time, switched by the dots under it or a sideways swipe; a folded
 * zone is one button listing them all. [open] is the layer in view; [onShow] opens one.
 */
@Composable
internal fun ActionZoneView(
    zone: Zone,
    lists: List<ZoneList>,
    open: Int,
    callbacks: ActionCallbacks,
    onShow: (layer: String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    if (zone.collapsed) {
        FoldedZone(zone, lists, callbacks, compact, modifier)
        return
    }
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(open)
    val show = { index: Int ->
        val layer = lists.getOrNull(index)?.layer
        if (index != current && layer != null) {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            onShow(layer.name)
        }
    }
    val paged = lists.size > 1
    val shape = RoundedCornerShape(ButtonRadius + Gap)
    Column(
        modifier = modifier
            .clip(shape)
            .background(LpmTheme.colors.secondarySystemGroupedBackground.copy(alpha = 0.6f), shape)
            .border(1.dp, LpmTheme.colors.label.copy(alpha = 0.14f), shape)
            .then(if (paged) Modifier.swipe { show(current + it) } else Modifier)
            .semantics { contentDescription = zone.label }
            .padding(Gap),
        verticalArrangement = Arrangement.spacedBy(Gap),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (paged) {
            val at by animateFloatAsState(open.toFloat(), SMOOTH, label = "layer")
            ZonePager(open = { at }, empty = lists.map { it.actions.isEmpty() }) {
                lists.forEachIndexed { index, list ->
                    key(list.key) { Page(list, zone.rows, index == open, callbacks, compact) }
                }
            }
            LayerDots(lists, open, show)
        } else {
            lists.firstOrNull()?.let { Grid(it, zone.rows, callbacks, compact) }
        }
    }
}

@Composable
private fun Page(
    list: ZoneList,
    rows: Int,
    isOpen: Boolean,
    callbacks: ActionCallbacks,
    compact: Boolean
) {
    val alpha by animateFloatAsState(if (isOpen) 1f else HIDDEN_ALPHA, SMOOTH, label = "page")
    val hidden = Modifier
        .alpha(alpha)
        .then(if (isOpen) Modifier else Modifier.clearAndSetSemantics {})
    if (list.actions.isEmpty()) {
        Box(hidden.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.actions_zone_no_actions),
                style = LpmTheme.typography.footnote,
                color = LpmTheme.colors.secondaryLabel
            )
        }
    } else {
        Grid(list, rows, callbacks, compact, hidden)
    }
}

@Composable
private fun Grid(
    list: ZoneList,
    rows: Int,
    callbacks: ActionCallbacks,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    ZoneGrid(rows = rows, spacing = Gap, modifier = modifier) {
        list.actions.forEach { action ->
            key(action.id) {
                DesktopActionButton(action, callbacks, compact = compact, fill = true)
            }
        }
    }
}

/** Folded on the desktop: the zone's name on one button whose menu holds its buttons. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FoldedZone(
    zone: Zone,
    lists: List<ZoneList>,
    callbacks: ActionCallbacks,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    var menu by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(modifier) {
        ActionFace(
            tint = null,
            compact = compact,
            pressed = pressed,
            modifier = Modifier
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = { menu = true }
                )
                .semantics(mergeDescendants = true) { contentDescription = zone.label },
            horizontalPadding = 14.dp
        ) { style ->
            FaceLabel(zone.label, style)
            Chevron(style, Modifier.padding(start = 6.dp))
        }
        ContextMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (lists.size > 1) {
                lists.filter { it.actions.isNotEmpty() }.forEach { list ->
                    MenuSectionHeader(list.title)
                    ActionMenuItems(list.actions, callbacks.run)
                }
            } else {
                ActionMenuItems(lists.firstOrNull()?.actions.orEmpty(), callbacks.run)
            }
        }
    }
}

@Composable
private fun MenuSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = LpmTheme.typography.footnote,
        color = LpmTheme.colors.secondaryLabel,
        modifier = modifier.padding(start = 28.dp, end = 27.dp, top = 10.dp, bottom = 4.dp)
    )
}

/** A sideways swipe of more than 40 dp: -1 opens the layer before, +1 the one after. */
private fun Modifier.swipe(onStep: (Int) -> Unit): Modifier = pointerInput(Unit) {
    var dx = 0f
    detectHorizontalDragGestures(
        onDragStart = { dx = 0f },
        onDragEnd = {
            if (abs(dx) > SwipeDistance.toPx()) onStep(if (dx < 0) 1 else -1)
        },
        onHorizontalDrag = { _, amount -> dx += amount }
    )
}

/** The corner of a button face inside, so the frame's corner sits concentric with it. */
private val ButtonRadius = 22.dp
internal val Gap = 4.dp
private val SwipeDistance = 40.dp
private const val HIDDEN_ALPHA = 0.12f
private val SMOOTH = tween<Float>(durationMillis = 340, easing = FastOutSlowInEasing)
