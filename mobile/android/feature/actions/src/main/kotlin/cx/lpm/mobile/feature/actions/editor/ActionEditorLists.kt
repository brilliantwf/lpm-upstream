package cx.lpm.mobile.feature.actions.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import cx.lpm.mobile.core.designsystem.R
import cx.lpm.mobile.core.designsystem.component.ButtonRow
import cx.lpm.mobile.core.designsystem.component.ListHeaderStyle
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.component.PickerRow
import cx.lpm.mobile.core.designsystem.component.TextFieldRow
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.trimmingWhitespace
import cx.lpm.mobile.feature.actions.R as ActionsR

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
