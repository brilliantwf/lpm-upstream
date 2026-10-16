package cx.lpm.mobile.feature.automations.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.component.ListDefaults
import cx.lpm.mobile.core.designsystem.component.ListHeaderStyle
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.component.PickerRow
import cx.lpm.mobile.core.designsystem.component.SegmentedControl
import cx.lpm.mobile.core.designsystem.component.StepperRow
import cx.lpm.mobile.core.designsystem.component.ToggleRow
import cx.lpm.mobile.core.designsystem.component.clickableNoIndication
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.automations.R

/**
 * "Frequency": when the job runs. A schedule set on the Mac that the form can't
 * rebuild is shown in words and left alone.
 */
@Composable
fun FrequencySection(
    state: AutomationEditorUiState,
    onEdit: DraftEdit,
    modifier: Modifier = Modifier
) {
    val draft = state.draft
    if (state.keptSchedule) {
        ListSection(
            modifier = modifier,
            header = stringResource(R.string.editor_frequency),
            headerStyle = ListHeaderStyle.Title,
            footer = stringResource(R.string.editor_kept_footer)
        ) {
            ListRow {
                Text(
                    text = state.keptScheduleText ?: stringResource(R.string.editor_kept_schedule),
                    style = LpmTheme.typography.body
                )
            }
        }
        return
    }
    ListSection(
        modifier = modifier,
        header = stringResource(R.string.editor_frequency),
        headerStyle = ListHeaderStyle.Title,
        footer = draft.scheduleSummary()
    ) {
        val repeats = mapOf(
            RepeatChoice.Daily to stringResource(R.string.editor_repeat_daily),
            RepeatChoice.Days to stringResource(R.string.editor_repeat_days),
            RepeatChoice.Interval to stringResource(R.string.editor_repeat_interval),
            RepeatChoice.Manual to stringResource(R.string.editor_repeat_manual)
        )
        PickerRow(
            title = stringResource(R.string.editor_repeat),
            options = RepeatChoice.entries,
            selected = draft.repeat,
            onSelect = { choice -> onEdit { it.withRepeat(choice) } },
            optionLabel = { repeats.getValue(it) }
        )
        when (draft.scheduleMode) {
            ScheduleMode.Time -> TimeRows(draft, onEdit)
            ScheduleMode.Interval -> IntervalRows(draft, onEdit)
            ScheduleMode.Manual -> Unit
        }
    }
}

@Composable
private fun TimeRows(draft: JobEditorDraft, onEdit: DraftEdit) {
    if (draft.days.isNotEmpty() || draft.repeat == RepeatChoice.Days) {
        WeekdayRow(draft.days, onToggle = { day -> onEdit { it.toggleDay(day) } })
    }
    if (draft.dayPool > 1) {
        val all = stringResource(R.string.editor_use_all)
        val labels = (1 until draft.dayPool).associateWith {
            pluralStringResource(R.plurals.editor_use_random, it, it)
        }
        PickerRow(
            title = stringResource(R.string.editor_use),
            options = (0 until draft.dayPool).toList(),
            selected = draft.pickDaysChoice,
            onSelect = { n -> onEdit { it.withPickDays(n) } },
            optionLabel = { labels[it] ?: all }
        )
    }
    ToggleRow(
        title = stringResource(R.string.editor_random_window),
        checked = draft.randomWindow,
        onCheckedChange = { on -> onEdit { it.copy(randomWindow = on) } }
    )
    TimePickerRow(
        title = stringResource(
            if (draft.randomWindow) R.string.editor_between else R.string.editor_at
        ),
        time = draft.time,
        fallbackMinutes = DEFAULT_AT,
        onTimeChange = { t -> onEdit { it.copy(time = t) } }
    )
    if (draft.randomWindow) {
        TimePickerRow(
            title = stringResource(R.string.editor_and),
            time = draft.untilTime,
            fallbackMinutes = DEFAULT_UNTIL,
            onTimeChange = { t -> onEdit { it.copy(untilTime = t) } }
        )
        StepperRow(
            title = stringResource(R.string.editor_runs_a_day),
            value = draft.timesPerDay,
            onValueChange = { n -> onEdit { it.copy(timesPerDay = n) } },
            range = 1..MAX_TIMES_PER_DAY
        )
    }
}

@Composable
private fun IntervalRows(draft: JobEditorDraft, onEdit: DraftEdit) {
    StepperRow(
        title = stringResource(
            if (draft.varyInterval) R.string.editor_between else R.string.editor_every
        ),
        value = draft.intervalValue,
        onValueChange = { n -> onEdit { it.withIntervalValue(n) } },
        range = 1..MAX_INTERVAL
    )
    ToggleRow(
        title = stringResource(R.string.editor_vary_gap),
        checked = draft.varyInterval,
        onCheckedChange = { on -> onEdit { it.withVaryInterval(on) } }
    )
    if (draft.varyInterval) {
        StepperRow(
            title = stringResource(R.string.editor_and),
            value = draft.intervalMaxValue,
            onValueChange = { n -> onEdit { it.copy(intervalMaxValue = n) } },
            range = draft.intervalValue..MAX_INTERVAL_SPREAD
        )
    }
    val units = IntervalUnit.entries
    ListRow(verticalPadding = 10.dp) {
        SegmentedControl(
            options = listOf(
                stringResource(R.string.editor_unit_minutes),
                stringResource(R.string.editor_unit_hours),
                stringResource(R.string.editor_unit_days)
            ),
            selectedIndex = units.indexOf(draft.intervalUnit),
            onSelect = { i -> onEdit { it.copy(intervalUnit = units[i]) } }
        )
    }
}

/** The seven day letters; a picked day fills with the tint. */
@Composable
private fun WeekdayRow(days: List<String>, onToggle: (String) -> Unit) {
    val colors = LpmTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ListDefaults.RowPadding, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        WEEKDAYS.forEach { day ->
            val on = day in days
            Box(
                Modifier
                    .size(34.dp)
                    .background(if (on) colors.tint else colors.secondarySystemFill, CircleShape)
                    .semantics { selected = on }
                    .clickableNoIndication(onClick = { onToggle(day) }),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = DAY_LETTER.getValue(day),
                    style = LpmTheme.typography.subheadline.copy(fontWeight = FontWeight.SemiBold),
                    color = if (on) Color.White else colors.label
                )
            }
        }
    }
}

private val DAY_LETTER = mapOf(
    "mon" to "M",
    "tue" to "T",
    "wed" to "W",
    "thu" to "T",
    "fri" to "F",
    "sat" to "S",
    "sun" to "S"
)

private const val DEFAULT_AT = 9 * 60
private const val DEFAULT_UNTIL = 17 * 60
private const val MAX_TIMES_PER_DAY = 48
private const val MAX_INTERVAL = 240
private const val MAX_INTERVAL_SPREAD = 480
