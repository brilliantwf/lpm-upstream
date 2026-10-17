package cx.lpm.mobile.feature.actions.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R
import cx.lpm.mobile.core.designsystem.component.ButtonRow
import cx.lpm.mobile.core.designsystem.component.ListHeaderStyle
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.component.PickerRow
import cx.lpm.mobile.core.designsystem.component.PlainTextField
import cx.lpm.mobile.core.designsystem.component.SwipeAction
import cx.lpm.mobile.core.designsystem.component.SwipeActionsBox
import cx.lpm.mobile.core.designsystem.component.TextFieldRow
import cx.lpm.mobile.core.designsystem.component.ToggleRow
import cx.lpm.mobile.core.designsystem.component.listSeparator
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.ActionInputDraft
import cx.lpm.mobile.core.model.ActionInputKind
import cx.lpm.mobile.core.model.ActionInputsProblem
import cx.lpm.mobile.core.model.ActionInputsYaml
import cx.lpm.mobile.core.model.ActionOptionDraft
import cx.lpm.mobile.core.model.trimmingWhitespace
import cx.lpm.mobile.feature.actions.R as ActionsR

/**
 * A command button's inputs, each its own section, then "Add input". The fields follow
 * the desktop wizard's question settings; what still keeps them from saving shows
 * under "Add input".
 */
@Composable
internal fun InputsSections(draft: ActionDraft, onEdit: DraftEdit) {
    draft.inputs.forEach { input ->
        key(input.id) {
            fun change(next: ActionInputDraft) = onEdit { d ->
                d.copy(inputs = d.inputs.map { if (it.id == input.id) next else it })
            }
            ListSection(
                header = input.trimmedKey.ifEmpty {
                    stringResource(ActionsR.string.actions_editor_input)
                },
                headerStyle = ListHeaderStyle.Title
            ) {
                InputFields(input, ::change)
                ButtonRow(
                    text = stringResource(ActionsR.string.actions_editor_input_remove),
                    onClick = {
                        onEdit { d -> d.copy(inputs = d.inputs.filterNot { it.id == input.id }) }
                    },
                    destructive = true
                )
            }
        }
    }
    val problem = ActionInputsYaml.problem(draft.inputs)
    ListSection(
        footer = problem?.let { problemText(it) }
            ?: stringResource(ActionsR.string.actions_editor_inputs_footer),
        footerColor = if (problem != null) LpmTheme.colors.red else LpmTheme.colors.secondaryLabel
    ) {
        ButtonRow(
            text = stringResource(ActionsR.string.actions_editor_input_add),
            onClick = { onEdit { it.copy(inputs = it.inputs + ActionInputDraft()) } },
            icon = painterResource(R.drawable.ic_add)
        )
    }
}

@Composable
private fun problemText(problem: ActionInputsProblem): String = when (problem) {
    ActionInputsProblem.BadName -> stringResource(ActionsR.string.actions_editor_input_bad_name)

    is ActionInputsProblem.Duplicate ->
        stringResource(ActionsR.string.actions_editor_input_duplicate, problem.key)

    is ActionInputsProblem.NoChoices ->
        stringResource(ActionsR.string.actions_editor_input_no_choices, problem.key)

    is ActionInputsProblem.StaleStart ->
        stringResource(ActionsR.string.actions_editor_input_stale_start, problem.key)
}

@Composable
private fun InputFields(input: ActionInputDraft, onChange: (ActionInputDraft) -> Unit) {
    val mono = LpmTheme.typography.callout.copy(fontFamily = FontFamily.Monospace)
    TextFieldRow(
        value = input.key,
        onValueChange = { onChange(input.copy(key = it)) },
        placeholder = stringResource(ActionsR.string.actions_editor_name),
        textStyle = mono,
        keyboardOptions = PlainKeyboard
    )
    TextFieldRow(
        value = input.label,
        onValueChange = { onChange(input.copy(label = it)) },
        placeholder = stringResource(ActionsR.string.actions_editor_label_optional)
    )
    val kinds = ActionInputKind.entries.associateWith { kindTitle(it) }
    PickerRow(
        title = stringResource(ActionsR.string.actions_editor_input_kind),
        options = kinds.keys.toList(),
        selected = input.kind,
        onSelect = { onChange(input.copy(kind = it)) },
        optionLabel = { kinds.getValue(it) }
    )
    ToggleRow(
        title = stringResource(ActionsR.string.actions_editor_input_required),
        checked = input.required,
        onCheckedChange = { onChange(input.copy(required = it)) }
    )
    if (input.kind == ActionInputKind.Radio) {
        ChoiceFields(input, onChange)
    } else {
        TextFieldRow(
            value = input.placeholder,
            onValueChange = { onChange(input.copy(placeholder = it)) },
            placeholder = stringResource(ActionsR.string.actions_editor_input_placeholder),
            keyboardOptions = PlainKeyboard
        )
        TextFieldRow(
            value = input.defaultValue,
            onValueChange = { onChange(input.copy(defaultValue = it)) },
            placeholder = stringResource(ActionsR.string.actions_editor_input_default),
            keyboardOptions = PlainKeyboard
        )
    }
    if (input.kind != ActionInputKind.Password) {
        ToggleRow(
            title = stringResource(ActionsR.string.actions_editor_input_persist),
            checked = input.persist,
            onCheckedChange = { onChange(input.copy(persist = it)) }
        )
    }
}

@Composable
private fun kindTitle(kind: ActionInputKind): String = stringResource(
    when (kind) {
        ActionInputKind.Text -> ActionsR.string.actions_editor_input_text
        ActionInputKind.Radio -> ActionsR.string.actions_editor_input_choice
        ActionInputKind.Password -> ActionsR.string.actions_editor_input_secret
    }
)

/** A choice input's choices, "Add choice", and the one it starts on. */
@Composable
private fun ChoiceFields(input: ActionInputDraft, onChange: (ActionInputDraft) -> Unit) {
    input.options.forEach { option ->
        key(option.id) { OptionRow(input, option, onChange) }
    }
    ButtonRow(
        text = stringResource(ActionsR.string.actions_editor_option_add),
        onClick = { onChange(input.copy(options = input.options + ActionOptionDraft())) },
        icon = painterResource(R.drawable.ic_add)
    )
    val nothing = stringResource(ActionsR.string.actions_editor_input_nothing_selected)
    PickerRow(
        title = stringResource(ActionsR.string.actions_editor_input_starts_as),
        options = listOf("") + startChoices(input),
        selected = input.defaultValue,
        onSelect = { onChange(input.copy(defaultValue = it)) },
        optionLabel = { it.ifEmpty { nothing } }
    )
}

/**
 * The choices a choice input can start on, plus a saved start that has since left
 * the list, so the picker still shows what the file holds.
 */
internal fun startChoices(input: ActionInputDraft): List<String> {
    val values = input.optionValues.distinct()
    val current = input.defaultValue.trimmingWhitespace()
    return if (current.isNotEmpty() && current !in values) values + current else values
}

/** A choice's value and label side by side; swipe to delete it. */
@Composable
private fun OptionRow(
    input: ActionInputDraft,
    option: ActionOptionDraft,
    onChange: (ActionInputDraft) -> Unit
) {
    fun change(next: ActionOptionDraft) =
        onChange(input.copy(options = input.options.map { if (it.id == option.id) next else it }))
    SwipeActionsBox(
        modifier = Modifier.listSeparator(),
        trailing = listOf(
            SwipeAction(
                stringResource(ActionsR.string.delete),
                R.drawable.ic_delete,
                LpmTheme.colors.red
            ) {
                onChange(input.copy(options = input.options.filterNot { it.id == option.id }))
            }
        )
    ) {
        ListRow {
            PlainTextField(
                value = option.value,
                onValueChange = { change(option.copy(value = it)) },
                placeholder = stringResource(ActionsR.string.actions_editor_option_value),
                keyboardOptions = PlainKeyboard,
                modifier = Modifier.weight(1f)
            )
            Box(
                Modifier
                    .padding(horizontal = 8.dp)
                    .width(1.dp)
                    .height(22.dp)
                    .background(LpmTheme.colors.listSeparator)
            )
            PlainTextField(
                value = option.label,
                onValueChange = { change(option.copy(label = it)) },
                placeholder = stringResource(ActionsR.string.actions_editor_label_optional),
                modifier = Modifier.weight(1f)
            )
        }
    }
}
