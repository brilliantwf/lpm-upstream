package cx.lpm.mobile.feature.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.SwipeAction
import cx.lpm.mobile.core.designsystem.component.SwipeActionsBox
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import kotlinx.coroutines.delay

/** What the list's rows and cards do when touched. */
class ActivityListActions(
    val open: (ActivityRow) -> Unit = {},
    val dismiss: (ActivityRow) -> Unit = {},
    val explain: (String) -> Unit = {},
    val runChip: (project: String, ActivityChip) -> Unit = { _, _ -> },
    val stop: (project: String) -> Unit = {},
    val openProject: (project: String) -> Unit = {}
)

/**
 * The rows and service cards. Dragging the list holds its order, so rows never move
 * out from under a thumb; the hold lets go a moment after the drag ends.
 */
@Composable
fun ActivityList(
    state: ActivityUiState,
    visible: VisibleActivity,
    now: Long,
    actions: ActivityListActions,
    modifier: Modifier = Modifier
) {
    var frozen by remember { mutableStateOf<Map<String, Int>?>(null) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(dragging) {
        if (!dragging && frozen != null) {
            delay(RESETTLE_MS)
            frozen = null
        }
    }
    val rows = heldOrder(visible.rows, frozen)
    // The drag detector outlives compositions; it freezes the rows shown now.
    val shown by rememberUpdatedState(rows)
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .onDrag(
                onStart = {
                    if (frozen == null) frozen = freezeOrder(shown)
                    dragging = true
                },
                onEnd = { dragging = false }
            )
    ) {
        if (frozen != null && orderIsStale(rows)) {
            item(key = "sort-now") {
                ActivitySortNowButton(onClick = { frozen = null }, modifier = RowInsets)
            }
        }
        if (state.grouped) {
            activityGroups(rows).forEach { group ->
                stickyHeader(key = "group:${group.id}") {
                    ActivitySectionHeader(
                        group.project,
                        Modifier.background(LpmTheme.colors.systemGroupedBackground)
                    )
                }
                rowCards(group.rows, now, hideProject = true, actions)
            }
        } else {
            rowCards(rows, now, hideProject = false, actions)
        }
        serviceCards(state, visible.services, actions)
        if (!visible.isEmpty) {
            item(key = "footer") {
                ActivityFooter(state.activity.quietProjectCount, RowInsets)
            }
        }
    }
}

private fun LazyListScope.rowCards(
    rows: List<ActivityRow>,
    now: Long,
    hideProject: Boolean,
    actions: ActivityListActions
) {
    items(rows, key = { it.id }) { row ->
        // Clipped to the card, so a swipe slides it within its own rounded corners.
        SwipeActionsBox(
            modifier = RowInsets.clip(CardShape),
            trailing = swipeActions(row, actions)
        ) {
            ActivityRowCard(row, now, onOpen = { actions.open(row) }, hideProject = hideProject)
        }
    }
}

@Composable
private fun swipeActions(row: ActivityRow, actions: ActivityListActions): List<SwipeAction> {
    val colors = LpmTheme.colors
    val blocked = row.dismissBlocked
    return when {
        row.dismissable -> listOf(
            SwipeAction(
                stringResource(R.string.activity_clear),
                DesignR.drawable.ic_close,
                colors.red
            ) { actions.dismiss(row) }
        )

        blocked != null -> listOf(
            SwipeAction(
                stringResource(R.string.activity_why),
                DesignR.drawable.ic_question_mark,
                colors.gray
            ) { actions.explain(blocked) }
        )

        else -> emptyList()
    }
}

private fun LazyListScope.serviceCards(
    state: ActivityUiState,
    services: List<ActivityServiceGroup>,
    actions: ActivityListActions
) {
    if (services.isEmpty()) return
    stickyHeader(key = "services") {
        ActivityServicesHeader(
            services,
            Modifier.background(LpmTheme.colors.systemGroupedBackground)
        )
    }
    items(services, key = { "service:${it.id}" }) { group ->
        val name = group.project.name
        ActivityServiceCard(
            group = group,
            pending = state.pendingServices[name].orEmpty(),
            stopping = name in state.stopping,
            onChip = { actions.runChip(name, it) },
            onStop = { actions.stop(name) },
            onOpenProject = { actions.openProject(name) },
            modifier = RowInsets
        )
    }
}

/** A row's room in the list, as `activityRowChrome` gives the iOS cards. */
private val RowInsets = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)

/**
 * Reports a drag over the list without taking it from the list or its rows: a tap
 * must still reach the card underneath, and a drag scrolls or swipes as usual.
 */
private fun Modifier.onDrag(onStart: () -> Unit, onEnd: () -> Unit): Modifier = pointerInput(Unit) {
    val slop = DRAG_START.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var started = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!started && (change.position - down.position).getDistance() > slop) {
                started = true
                onStart()
            }
            if (!change.pressed) break
        }
        if (started) onEnd()
    }
}

private val DRAG_START = 10.dp

/** How long a held order outlives the drag that started it. */
private const val RESETTLE_MS = 1_200L
