package cx.lpm.mobile.feature.projects.services

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import cx.lpm.mobile.core.designsystem.component.listSeparator
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.trimmingWhitespace
import cx.lpm.mobile.feature.projects.R as ProjectsR
import cx.lpm.mobile.feature.projects.ui.PlainKeyboard

/** The service form: name, command, then the advanced fields, dependencies and env. */
@Composable
fun ServiceForm(
    state: ServiceEditorUiState,
    onEdit: ((ServiceDraft) -> ServiceDraft) -> Unit,
    onDelete: () -> Unit
) {
    val draft = state.draft
    ListSection(footer = stringResource(ProjectsR.string.service_name_footer)) {
        TextFieldRow(
            value = draft.name,
            onValueChange = { v -> onEdit { it.copy(name = v) } },
            placeholder = stringResource(ProjectsR.string.service_name),
            keyboardOptions = PlainKeyboard
        )
    }
    ListSection(
        header = stringResource(ProjectsR.string.service_command),
        headerStyle = ListHeaderStyle.Title
    ) {
        TextFieldRow(
            value = draft.cmd,
            onValueChange = { v -> onEdit { it.copy(cmd = v) } },
            placeholder = "npm run dev",
            textStyle = LpmTheme.typography.body.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = PlainKeyboard,
            maxLines = 4
        )
    }
    ListSection(
        header = stringResource(ProjectsR.string.service_advanced),
        headerStyle = ListHeaderStyle.Title
    ) {
        LabeledField(
            title = stringResource(ProjectsR.string.service_cwd),
            value = draft.cwd,
            onValueChange = { v -> onEdit { it.copy(cwd = v) } },
            placeholder = "apps/api",
            style = LpmTheme.typography.callout.copy(fontFamily = FontFamily.Monospace)
        )
        LabeledField(
            title = stringResource(ProjectsR.string.service_port),
            value = draft.port,
            onValueChange = { v -> onEdit { it.copy(port = v) } },
            placeholder = "3000",
            keyboardType = KeyboardType.Number
        )
    }
    if (draft.port.trimmingWhitespace().isNotEmpty()) PortConflictSection(draft, onEdit)
    if (state.otherServices.isNotEmpty()) DependsOnSection(draft, state.otherServices, onEdit)
    EnvSection(draft, onEdit)
    if (state.editing) {
        ListSection {
            ButtonRow(
                text = stringResource(ProjectsR.string.service_delete),
                onClick = onDelete,
                destructive = true,
                enabled = !state.saving
            )
        }
    }
}

@Composable
private fun PortConflictSection(
    draft: ServiceDraft,
    onEdit: ((ServiceDraft) -> ServiceDraft) -> Unit
) {
    val labels = mapOf(
        PortConflict.Ask to stringResource(ProjectsR.string.service_port_ask),
        PortConflict.Free to stringResource(ProjectsR.string.service_port_free),
        PortConflict.Fail to stringResource(ProjectsR.string.service_port_fail)
    )
    val hint = when (draft.portConflict) {
        PortConflict.Free -> ProjectsR.string.service_port_free_hint
        PortConflict.Fail -> ProjectsR.string.service_port_fail_hint
        PortConflict.Ask -> ProjectsR.string.service_port_ask_hint
    }
    ListSection(footer = stringResource(hint)) {
        PickerRow(
            title = stringResource(ProjectsR.string.service_port_busy),
            options = PortConflict.entries,
            selected = draft.portConflict,
            onSelect = { v -> onEdit { it.copy(portConflict = v) } },
            optionLabel = { labels.getValue(it) }
        )
    }
}

@Composable
private fun DependsOnSection(
    draft: ServiceDraft,
    others: List<String>,
    onEdit: ((ServiceDraft) -> ServiceDraft) -> Unit
) {
    ListSection(
        header = stringResource(ProjectsR.string.service_depends_on),
        headerStyle = ListHeaderStyle.Title,
        footer = stringResource(ProjectsR.string.service_depends_on_footer)
    ) {
        others.forEach { name ->
            ListRow(
                onClick = { onEdit { it.toggleDependsOn(name) } },
                trailing = {
                    if (name in draft.dependsOn) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            tint = LpmTheme.colors.tint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            ) {
                Text(name, style = LpmTheme.typography.body, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EnvSection(draft: ServiceDraft, onEdit: ((ServiceDraft) -> ServiceDraft) -> Unit) {
    val delete = stringResource(ProjectsR.string.service_env_delete)
    val style = LpmTheme.typography.callout.copy(fontFamily = FontFamily.Monospace)
    ListSection(
        header = stringResource(ProjectsR.string.service_env),
        headerStyle = ListHeaderStyle.Title
    ) {
        draft.env.forEach { row ->
            fun change(next: EnvRow) = onEdit { d ->
                d.copy(
                    env = d.env.map {
                        if (it.id ==
                            row.id
                        ) {
                            next
                        } else {
                            it
                        }
                    }
                )
            }
            SwipeActionsBox(
                modifier = Modifier.listSeparator(),
                trailing = listOf(
                    SwipeAction(delete, R.drawable.ic_delete, LpmTheme.colors.red) {
                        onEdit { d -> d.copy(env = d.env.filterNot { it.id == row.id }) }
                    }
                )
            ) {
                ListRow {
                    PlainTextField(
                        value = row.key,
                        onValueChange = { change(row.copy(key = it)) },
                        placeholder = "KEY",
                        textStyle = style,
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
                        value = row.value,
                        onValueChange = { change(row.copy(value = it)) },
                        placeholder = "value",
                        textStyle = style,
                        keyboardOptions = PlainKeyboard,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        ButtonRow(
            text = stringResource(ProjectsR.string.service_env_add),
            onClick = { onEdit { it.copy(env = it.env + EnvRow()) } },
            icon = painterResource(R.drawable.ic_add)
        )
    }
}

/** A row's title with its value typed in at the trailing edge (`LabeledContent`). */
@Composable
private fun LabeledField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LpmTheme.typography.body,
    keyboardType: KeyboardType = KeyboardType.Text
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
            keyboardOptions = PlainKeyboard.copy(keyboardType = keyboardType),
            modifier = Modifier.weight(1f)
        )
    }
}
