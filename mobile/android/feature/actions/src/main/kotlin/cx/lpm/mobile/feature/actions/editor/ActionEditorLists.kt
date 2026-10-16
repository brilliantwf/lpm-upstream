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
import cx.lpm.mobile.core.model.trimmingWhitespace
import cx.lpm.mobile.feature.actions.R as ActionsR

/** A command button's inputs, each its own section, then "Add input". */
@Composable
internal fun InputsSections(draft: ActionDraft, onEdit: DraftEdit) {
    draft.inputs.forEach { input ->
        key(input.id) {
            fun change(next: InputDraft) = onEdit { d ->
                d.copy(inputs = d.inputs.map { if (it.id == input.id) next else it })
            }
            ListSection(
                header = input.key.trimmingWhitespace().ifEmpty {
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
    ListSection(footer = stringResource(ActionsR.string.actions_editor_inputs_footer)) {
        ButtonRow(
            text = stringResource(ActionsR.string.actions_editor_input_add),
            onClick = { onEdit { it.copy(inputs = it.inputs + InputDraft()) } },
            icon = painterResource(R.drawable.ic_add)
        )
    }
}

@Composable
private fun InputFields(input: InputDraft, onChange: (InputDraft) -> Unit) {
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
    val kinds = mapOf(
        "" to stringResource(ActionsR.string.actions_editor_input_text),
        InputDraft.SELECT to stringResource(ActionsR.string.actions_editor_input_choice)
    )
    PickerRow(
        title = stringResource(ActionsR.string.actions_editor_input_kind),
        options = kinds.keys.toList(),
        selected = input.type,
        onSelect = { onChange(input.copy(type = it)) },
        optionLabel = { kinds.getValue(it) }
    )
    ToggleRow(
        title = stringResource(ActionsR.string.actions_editor_input_required),
        checked = input.required,
        onCheckedChange = { onChange(input.copy(required = it)) }
    )
    if (input.isSelect) {
        input.options.forEach { option ->
            key(option.id) { OptionRow(input, option, onChange) }
        }
        ButtonRow(
            text = stringResource(ActionsR.string.actions_editor_option_add),
            onClick = { onChange(input.copy(options = input.options + OptionDraft())) },
            icon = painterResource(R.drawable.ic_add)
        )
    } else {
        TextFieldRow(
            value = input.placeholder,
            onValueChange = { onChange(input.copy(placeholder = it)) },
            placeholder = stringResource(ActionsR.string.actions_editor_input_placeholder)
        )
        TextFieldRow(
            value = input.defaultValue,
            onValueChange = { onChange(input.copy(defaultValue = it)) },
            placeholder = stringResource(ActionsR.string.actions_editor_input_default)
        )
    }
}

/** A choice's value and label side by side; swipe to delete it. */
@Composable
private fun OptionRow(input: InputDraft, option: OptionDraft, onChange: (InputDraft) -> Unit) {
    fun change(next: OptionDraft) =
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

/** A menu's items, each its own section, then "Add item". */
@Composable
internal fun ChildrenSections(draft: ActionDraft, onEdit: DraftEdit) {
    draft.children.forEach { child ->
        key(child.id) {
            fun change(next: ChildDraft) = onEdit { d ->
                d.copy(children = d.children.map { if (it.id == child.id) next else it })
            }
            ListSection(header = child.title(), headerStyle = ListHeaderStyle.Title) {
                TextFieldRow(
                    value = child.label,
                    onValueChange = { change(child.copy(label = it)) },
                    placeholder = stringResource(ActionsR.string.actions_editor_item_label)
                )
                TextFieldRow(
                    value = child.cmd,
                    onValueChange = { change(child.copy(cmd = it)) },
                    placeholder = stringResource(ActionsR.string.actions_editor_command),
                    textStyle = LpmTheme.typography.callout.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = PlainKeyboard,
                    maxLines = 4
                )
                RunsInPicker(child.runIn) { change(child.copy(runIn = it)) }
                ButtonRow(
                    text = stringResource(ActionsR.string.actions_editor_item_remove),
                    onClick = {
                        onEdit { d ->
                            d.copy(children = d.children.filterNot { it.id == child.id })
                        }
                    },
                    destructive = true
                )
            }
        }
    }
    ListSection(footer = stringResource(ActionsR.string.actions_editor_items_footer)) {
        ButtonRow(
            text = stringResource(ActionsR.string.actions_editor_item_add),
            onClick = { onEdit { it.copy(children = it.children + ChildDraft()) } },
            icon = painterResource(R.drawable.ic_add)
        )
    }
}

/** What the menu's button runs: nothing (it opens), the last item used, or one item. */
@Composable
internal fun PrimarySection(draft: ActionDraft, onEdit: DraftEdit) {
    val items = draft.children.filter { it.hasCommand }
    val titles = buildMap<PrimaryChoice, String> {
        put(PrimaryChoice.Menu, stringResource(ActionsR.string.actions_editor_primary_menu))
        put(
            PrimaryChoice.LastUsed,
            stringResource(ActionsR.string.actions_editor_primary_last_used)
        )
        items.forEach { put(PrimaryChoice.Child(it.id), it.title()) }
    }
    ListSection(footer = stringResource(ActionsR.string.actions_editor_primary_footer)) {
        PickerRow(
            title = stringResource(ActionsR.string.actions_editor_primary),
            options = titles.keys.toList(),
            selected = draft.primary,
            onSelect = { choice -> onEdit { it.copy(primary = choice) } },
            optionLabel = { titles[it].orEmpty() }
        )
    }
}

@Composable
private fun ChildDraft.title(): String =
    label.trimmingWhitespace().ifEmpty { stringResource(ActionsR.string.actions_editor_item) }
