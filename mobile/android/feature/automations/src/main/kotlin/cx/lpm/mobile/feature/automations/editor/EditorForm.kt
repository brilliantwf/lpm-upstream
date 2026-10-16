package cx.lpm.mobile.feature.automations.editor

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ListHeaderStyle
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.component.PickerRow
import cx.lpm.mobile.core.designsystem.component.TextFieldRow
import cx.lpm.mobile.core.designsystem.component.ValueRow
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.automations.R

/** Changes the form, the way [AutomationEditorViewModel.edit] does. */
typealias DraftEdit = ((JobEditorDraft) -> JobEditorDraft) -> Unit

/** Keys a code or a model name is typed with: no autocorrect, no capitals. */
internal val PlainKeyboard = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false
)

/** The editor's form, section by section as the iOS sheet lays it out. */
@Composable
fun EditorForm(
    state: AutomationEditorUiState,
    onEdit: DraftEdit,
    deleteCopies: Boolean,
    onDeleteCopiesChange: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    NameSection(state.draft, onEdit)
    ScopeSection(state, onEdit)
    RunSection(state, onEdit)
    FrequencySection(state, onEdit)
    AdvancedSections(state.draft, onEdit)
    if (state.deletable) DeleteSection(state, deleteCopies, onDeleteCopiesChange, onDelete)
}

@Composable
private fun NameSection(draft: JobEditorDraft, onEdit: DraftEdit) {
    ListSection {
        TextFieldRow(
            value = draft.label,
            onValueChange = { v -> onEdit { it.copy(label = v) } },
            placeholder = stringResource(R.string.editor_name)
        )
        TextFieldRow(
            value = draft.emoji,
            onValueChange = { v -> onEdit { it.withEmoji(v) } },
            placeholder = stringResource(R.string.editor_emoji),
            keyboardOptions = PlainKeyboard
        )
    }
}

@Composable
private fun ScopeSection(state: AutomationEditorUiState, onEdit: DraftEdit) {
    val targets = state.draft.targets
    val footer = when {
        !state.scopeEditable -> null
        targets.isEmpty() -> stringResource(R.string.editor_standalone_footer)
        targets.size == 1 -> stringResource(R.string.editor_target_footer)
        else -> stringResource(R.string.editor_targets_footer)
    }
    ListSection(
        header = stringResource(R.string.editor_runs_in),
        headerStyle = ListHeaderStyle.Title,
        footer = footer
    ) {
        if (!state.scopeEditable) {
            ValueRow(stringResource(R.string.editor_runs_in), state.scopeLabel)
            return@ListSection
        }
        state.projects.forEach { project ->
            ListRow(
                onClick = { onEdit { it.toggleTarget(project.name) } },
                trailing = {
                    if (project.name in targets) {
                        Icon(
                            painter = painterResource(DesignR.drawable.ic_check),
                            contentDescription = null,
                            tint = LpmTheme.colors.tint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            ) {
                // A button's label takes the tint, as in an iOS form.
                Text(
                    text = project.label,
                    style = LpmTheme.typography.body,
                    color = LpmTheme.colors.tint,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RunSection(state: AutomationEditorUiState, onEdit: DraftEdit) {
    val draft = state.draft
    val modes = buildList {
        add(RunMode.Prompt)
        add(RunMode.Cmd)
        if (state.actions.isNotEmpty() || draft.runMode == RunMode.Action) add(RunMode.Action)
    }
    val modeLabels = mapOf(
        RunMode.Prompt to stringResource(R.string.editor_mode_prompt),
        RunMode.Cmd to stringResource(R.string.editor_mode_cmd),
        RunMode.Action to stringResource(R.string.editor_mode_action)
    )
    ListSection(
        header = stringResource(R.string.editor_does),
        headerStyle = ListHeaderStyle.Title,
        footer = if (draft.runMode == RunMode.Prompt) {
            stringResource(R.string.editor_access_footer)
        } else {
            null
        }
    ) {
        PickerRow(
            title = stringResource(R.string.editor_does),
            options = modes,
            selected = draft.runMode,
            onSelect = { mode -> onEdit { it.copy(runMode = mode) } },
            optionLabel = { modeLabels.getValue(it) }
        )
        when (draft.runMode) {
            RunMode.Cmd -> TextFieldRow(
                value = draft.cmd,
                onValueChange = { v -> onEdit { it.copy(cmd = v) } },
                placeholder = "npm run refresh-fixtures",
                textStyle = LpmTheme.typography.body.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = PlainKeyboard,
                maxLines = FIELD_MAX_LINES
            )

            RunMode.Action -> ActionRow(state, onEdit)

            RunMode.Prompt -> PromptRows(draft, onEdit)
        }
    }
}

@Composable
private fun ActionRow(state: AutomationEditorUiState, onEdit: DraftEdit) {
    val draft = state.draft
    if (state.actions.isEmpty()) {
        ValueRow(stringResource(R.string.editor_action), draft.action)
        return
    }
    val labels = state.actions.associate { it.name to it.label }
    PickerRow(
        title = stringResource(R.string.editor_action),
        options = state.actions.map { it.name },
        selected = draft.action,
        onSelect = { name -> onEdit { it.copy(action = name) } },
        optionLabel = { labels[it] ?: it }
    )
}

@Composable
private fun PromptRows(draft: JobEditorDraft, onEdit: DraftEdit) {
    TextFieldRow(
        value = draft.prompt,
        onValueChange = { v -> onEdit { it.copy(prompt = v) } },
        placeholder = stringResource(R.string.editor_prompt),
        fieldModifier = Modifier.heightIn(min = 44.dp),
        maxLines = PROMPT_MAX_LINES
    )
    val agents = mapOf(
        "" to stringResource(R.string.editor_agent_default),
        "claude" to stringResource(R.string.editor_agent_claude),
        "codex" to stringResource(R.string.editor_agent_codex),
        "gemini" to stringResource(R.string.editor_agent_gemini),
        "opencode" to stringResource(R.string.editor_agent_opencode)
    )
    PickerRow(
        title = stringResource(R.string.editor_agent),
        options = agents.keys.toList(),
        selected = draft.agent,
        onSelect = { agent -> onEdit { it.copy(agent = agent) } },
        optionLabel = { agents[it] ?: it }
    )
    TextFieldRow(
        value = draft.model,
        onValueChange = { v -> onEdit { it.copy(model = v) } },
        placeholder = stringResource(R.string.editor_model),
        keyboardOptions = PlainKeyboard
    )
    if (draft.effortAvailable) {
        val efforts = mapOf(
            "" to stringResource(R.string.editor_agent_default),
            "low" to stringResource(R.string.editor_effort_low),
            "medium" to stringResource(R.string.editor_effort_medium),
            "high" to stringResource(R.string.editor_effort_high),
            "xhigh" to stringResource(R.string.editor_effort_xhigh),
            "max" to stringResource(R.string.editor_effort_max),
            "ultra" to stringResource(R.string.editor_effort_ultra)
        )
        PickerRow(
            title = stringResource(R.string.editor_effort),
            options = draft.effortOptions,
            selected = draft.effort,
            onSelect = { effort -> onEdit { it.copy(effort = effort) } },
            optionLabel = { efforts[it] ?: it }
        )
    }
    val full = stringResource(R.string.editor_access_full)
    val read = stringResource(R.string.editor_access_read)
    PickerRow(
        title = stringResource(R.string.editor_access),
        options = listOf(false, true),
        selected = draft.readOnly,
        onSelect = { readOnly -> onEdit { it.copy(readOnly = readOnly) } },
        optionLabel = { if (it) read else full }
    )
}

private const val FIELD_MAX_LINES = 6
private const val PROMPT_MAX_LINES = 8
