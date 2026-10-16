package cx.lpm.mobile.feature.actions.editor

import android.icu.text.BreakIterator
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.component.ListHeaderStyle
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.component.PickerRow
import cx.lpm.mobile.core.designsystem.component.PlainTextField
import cx.lpm.mobile.core.designsystem.component.TextFieldRow
import cx.lpm.mobile.core.designsystem.component.ToggleRow
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.Zone
import cx.lpm.mobile.feature.actions.R

internal typealias DraftEdit = ((ActionDraft) -> ActionDraft) -> Unit

/**
 * The action form: name and looks, a command button's command and where it runs (or
 * a menu's items), where the button sits, its color, and Delete when editing.
 */
@Composable
internal fun ActionForm(state: ActionEditorUiState, onEdit: DraftEdit, onDelete: () -> Unit) {
    val draft = state.draft
    BasicsSection(draft, onEdit)
    val kinds = ActionKind.entries.associateWith { kindTitle(it) }
    ListSection {
        PickerRow(
            title = stringResource(R.string.actions_editor_type),
            options = ActionKind.entries,
            selected = draft.kind,
            onSelect = { kind -> onEdit { it.copy(kind = kind) } },
            optionLabel = { kinds.getValue(it) }
        )
    }
    if (draft.kind == ActionKind.Command) {
        CommandSections(draft, onEdit)
        InputsSections(draft, onEdit)
    } else {
        ChildrenSections(draft, onEdit)
        PrimarySection(draft, onEdit)
    }
    PlacementSection(draft, state.zones, state.placementOptions, onEdit)
    ListSection(
        header = stringResource(R.string.actions_editor_color),
        headerStyle = ListHeaderStyle.Title
    ) {
        ActionColorPicker(draft.color) { color -> onEdit { it.copy(color = color) } }
    }
    if (state.editing) {
        ListSection {
            ListRow(onClick = onDelete, enabled = !state.saving) {
                Text(
                    text = stringResource(R.string.actions_editor_delete),
                    style = LpmTheme.typography.body,
                    color = with(LpmTheme.colors) { if (state.saving) tertiaryLabel else red },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BasicsSection(draft: ActionDraft, onEdit: DraftEdit) {
    ListSection(footer = stringResource(R.string.actions_editor_basics_footer)) {
        TextFieldRow(
            value = draft.name,
            onValueChange = { v -> onEdit { it.copy(name = v) } },
            placeholder = stringResource(R.string.actions_editor_name),
            keyboardOptions = PlainKeyboard
        )
        TextFieldRow(
            value = draft.label,
            onValueChange = { v -> onEdit { it.copy(label = v) } },
            placeholder = stringResource(R.string.actions_editor_label_optional)
        )
        TextFieldRow(
            value = draft.emoji,
            onValueChange = { v -> onEdit { it.copy(emoji = firstCharacter(v)) } },
            placeholder = stringResource(R.string.actions_editor_emoji_optional),
            keyboardOptions = PlainKeyboard
        )
    }
}

/** One emoji: anything typed after the first character is dropped. */
private fun firstCharacter(text: String): String {
    val breaks = BreakIterator.getCharacterInstance().apply { setText(text) }
    val end = breaks.next()
    return if (end == BreakIterator.DONE) text else text.substring(0, end)
}

@Composable
private fun CommandSections(draft: ActionDraft, onEdit: DraftEdit) {
    ListSection(
        header = stringResource(R.string.actions_editor_command),
        headerStyle = ListHeaderStyle.Title
    ) {
        TextFieldRow(
            value = draft.cmd,
            onValueChange = { v -> onEdit { it.copy(cmd = v) } },
            placeholder = "npm run deploy",
            textStyle = LpmTheme.typography.body.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = PlainKeyboard,
            maxLines = 4
        )
    }
    ListSection(footer = runInHint(draft)) {
        LabeledField(
            title = stringResource(R.string.actions_editor_cwd),
            value = draft.cwd,
            onValueChange = { v -> onEdit { it.copy(cwd = v) } },
            placeholder = "./backend",
            style = LpmTheme.typography.callout.copy(fontFamily = FontFamily.Monospace)
        )
        RunsInPicker(draft.runIn) { runIn -> onEdit { it.copy(runIn = runIn) } }
        if (draft.runIn == ActionRunIn.Terminal) {
            ToggleRow(
                title = stringResource(R.string.actions_editor_reuse),
                checked = draft.reuse,
                onCheckedChange = { v -> onEdit { it.copy(reuse = v) } }
            )
        }
        ToggleRow(
            title = stringResource(R.string.actions_editor_confirm),
            checked = draft.confirm,
            onCheckedChange = { v -> onEdit { it.copy(confirm = v) } }
        )
    }
}

@Composable
internal fun RunsInPicker(selected: ActionRunIn, onSelect: (ActionRunIn) -> Unit) {
    val titles = ActionRunIn.entries.associateWith { runInTitle(it) }
    PickerRow(
        title = stringResource(R.string.actions_editor_runs_in),
        options = ActionRunIn.entries,
        selected = selected,
        onSelect = onSelect,
        optionLabel = { titles.getValue(it) }
    )
}

@Composable
private fun PlacementSection(
    draft: ActionDraft,
    zones: List<Zone>,
    options: List<ActionPlacement>,
    onEdit: DraftEdit
) {
    val titles = options.associateWith { placementTitle(it, zones) }
    ListSection(
        header = stringResource(R.string.actions_editor_placement),
        headerStyle = ListHeaderStyle.Title,
        footer = placementHint(draft.placement, zones)
    ) {
        PickerRow(
            title = stringResource(R.string.actions_editor_placement),
            options = options,
            selected = draft.placement,
            onSelect = { placement -> onEdit { it.copy(placement = placement) } },
            optionLabel = { titles[it] ?: "" }
        )
    }
}

/** A row's title with its value typed in at the trailing edge (`LabeledContent`). */
@Composable
internal fun LabeledField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LpmTheme.typography.body
) {
    ListRow(modifier = modifier) {
        Text(title, style = LpmTheme.typography.body)
        Spacer(Modifier.width(12.dp))
        PlainTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            textStyle = style,
            textAlign = TextAlign.End,
            keyboardOptions = PlainKeyboard,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun kindTitle(kind: ActionKind): String = stringResource(
    when (kind) {
        ActionKind.Command -> R.string.actions_editor_kind_command
        ActionKind.Menu -> R.string.actions_editor_kind_menu
    }
)

@Composable
private fun runInTitle(runIn: ActionRunIn): String = stringResource(
    when (runIn) {
        ActionRunIn.Popup -> R.string.actions_editor_run_popup
        ActionRunIn.Terminal -> R.string.actions_editor_run_terminal
        ActionRunIn.Command -> R.string.actions_editor_run_command
        ActionRunIn.Background -> R.string.actions_editor_run_background
    }
)

@Composable
private fun runInHint(draft: ActionDraft): String = stringResource(
    when (draft.runIn) {
        ActionRunIn.Popup -> R.string.actions_editor_hint_popup

        ActionRunIn.Terminal ->
            if (draft.reuse) {
                R.string.actions_editor_hint_reuse
            } else {
                R.string.actions_editor_hint_terminal
            }

        ActionRunIn.Command -> R.string.actions_editor_hint_command

        ActionRunIn.Background -> R.string.actions_editor_hint_background
    }
)

@Composable
internal fun placementTitle(placement: ActionPlacement, zones: List<Zone>): String =
    when (placement) {
        ActionPlacement.Header -> stringResource(R.string.actions_placement_top)

        ActionPlacement.Footer -> stringResource(R.string.actions_placement_bottom)

        is ActionPlacement.InZone -> {
            val zone = zones.firstOrNull { it.name == placement.zone }
            val index = zone?.layers?.indexOfFirst { it.name == placement.layer } ?: -1
            when {
                zone == null ->
                    stringResource(R.string.actions_placement_unknown_zone, placement.zone)

                index < 0 -> zone.label

                else -> stringResource(
                    R.string.actions_placement_layer,
                    zone.label,
                    zone.layers[index].title(index)
                )
            }
        }
    }

/** Which bar a zone placement sits in. */
@Composable
internal fun placementHint(placement: ActionPlacement, zones: List<Zone>): String? {
    val name = (placement as? ActionPlacement.InZone)?.zone ?: return null
    val zone = zones.firstOrNull { it.name == name } ?: return null
    return stringResource(
        if (zone.isFooter) {
            R.string.actions_placement_hint_bottom
        } else {
            R.string.actions_placement_hint_top
        },
        zone.label
    )
}
