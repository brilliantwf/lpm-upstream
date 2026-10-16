package cx.lpm.mobile.feature.projects.duplicate

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.component.GlassCapsuleButton
import cx.lpm.mobile.core.designsystem.component.ListDefaults
import cx.lpm.mobile.core.designsystem.component.ListHeaderStyle
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.component.LpmTopBar
import cx.lpm.mobile.core.designsystem.component.ModalSheet
import cx.lpm.mobile.core.designsystem.component.PickerRow
import cx.lpm.mobile.core.designsystem.component.SegmentedControl
import cx.lpm.mobile.core.designsystem.component.StepperRow
import cx.lpm.mobile.core.designsystem.component.TextFieldRow
import cx.lpm.mobile.core.designsystem.component.ToggleRow
import cx.lpm.mobile.core.designsystem.component.rememberModalSheetState
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.DuplicateOptions
import cx.lpm.mobile.core.model.Project
import cx.lpm.mobile.core.model.RunMode
import cx.lpm.mobile.feature.projects.R
import cx.lpm.mobile.feature.projects.ui.PlainKeyboard

/**
 * The desktop's "Duplicate project" modal: how many copies, a label for each, a
 * sidebar folder for a batch, a task to run in every copy, and the three git
 * toggles. Confirming hands the options back; the copies stream in through the
 * projects-changed push.
 */
@Composable
fun DuplicateOptionsSheet(
    project: Project,
    defaults: DuplicateOptions,
    onConfirm: (DuplicateOptions) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    seedPrompt: String? = null,
    seedCount: Int? = null
) {
    var options by remember(project.name) {
        mutableStateOf(duplicateSeed(project, defaults, seedPrompt, seedCount))
    }
    val single = options.count == 1
    val confirm = if (options.runMode != RunMode.None) {
        if (single) {
            stringResource(R.string.duplicate_run_one)
        } else {
            pluralStringResource(R.plurals.duplicate_run_many, options.count, options.count)
        }
    } else {
        pluralStringResource(R.plurals.duplicate_create, options.count, options.count)
    }
    val sheet = rememberModalSheetState()
    ModalSheet(onDismissRequest = onDismiss, modifier = modifier, state = sheet) {
        // The bar's title gives way to the buttons rather than running under them:
        // "Create 12 copies" is wider than a sheet's usual Done.
        LpmTopBar(
            title = stringResource(R.string.duplicate_title, project.label),
            modifier = Modifier.padding(top = 16.dp),
            windowInsets = WindowInsets(0),
            leading = {
                GlassCapsuleButton(
                    text = stringResource(R.string.cancel),
                    onClick = { sheet.dismiss(onDismiss) },
                    textStyle = LpmTheme.typography.body
                )
            },
            trailing = {
                GlassCapsuleButton(
                    text = confirm,
                    onClick = {
                        onConfirm(options)
                        sheet.dismiss(onDismiss)
                    },
                    textStyle = LpmTheme.typography.headline
                )
            }
        )
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(top = ListDefaults.ContentTopPadding)
        ) {
            CopiesSection(options, single) { options = it }
            if (!single) LabelsSections(options) { options = it }
            RunSection(project, options) { options = it }
            ListSection(
                header = stringResource(R.string.duplicate_options),
                headerStyle = ListHeaderStyle.Title
            ) {
                ToggleRow(
                    title = stringResource(R.string.duplicate_committed_only),
                    checked = options.excludeUncommitted,
                    onCheckedChange = { options = options.copy(excludeUncommitted = it) }
                )
                ToggleRow(
                    title = stringResource(R.string.duplicate_pull_latest),
                    checked = options.pullLatest,
                    onCheckedChange = { options = options.copy(pullLatest = it) }
                )
                ToggleRow(
                    title = stringResource(R.string.duplicate_reinstall),
                    checked = options.reinstallDeps,
                    onCheckedChange = { options = options.copy(reinstallDeps = it) }
                )
            }
        }
    }
}

@Composable
private fun CopiesSection(
    options: DuplicateOptions,
    single: Boolean,
    onChange: (DuplicateOptions) -> Unit
) {
    ListSection(
        header = stringResource(R.string.duplicate_copies),
        headerStyle = ListHeaderStyle.Title,
        footer = if (single) stringResource(R.string.duplicate_single_footer) else null
    ) {
        StepperRow(
            title = stringResource(R.string.duplicate_copies),
            value = options.count,
            onValueChange = { onChange(options.withCount(it)) },
            range = 1..MAX_COPIES
        )
        if (single) {
            TextFieldRow(
                value = options.labels.firstOrNull().orEmpty(),
                onValueChange = { onChange(options.copy(labels = listOf(it))) },
                placeholder = stringResource(R.string.duplicate_auto_named),
                keyboardOptions = PlainKeyboard
            )
        }
    }
}

@Composable
private fun LabelsSections(options: DuplicateOptions, onChange: (DuplicateOptions) -> Unit) {
    ListSection(
        header = stringResource(R.string.duplicate_labels),
        headerStyle = ListHeaderStyle.Title,
        footer = stringResource(R.string.duplicate_labels_footer)
    ) {
        options.labels.forEachIndexed { i, label ->
            TextFieldRow(
                value = label,
                onValueChange = { text ->
                    onChange(
                        options.copy(
                            labels = options.labels.toMutableList().also {
                                it[i] =
                                    text
                            }
                        )
                    )
                },
                placeholder = stringResource(R.string.duplicate_copy_auto_named, i + 1),
                keyboardOptions = PlainKeyboard
            )
        }
    }
    ListSection(footer = stringResource(R.string.duplicate_folder_footer)) {
        TextFieldRow(
            value = options.groupName,
            onValueChange = { onChange(options.copy(groupName = it)) },
            placeholder = stringResource(R.string.duplicate_folder_name),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false)
        )
    }
}

@Composable
private fun RunSection(
    project: Project,
    options: DuplicateOptions,
    onChange: (DuplicateOptions) -> Unit
) {
    val modes = project.duplicateRunModes()
    val labels = mapOf(
        RunMode.None to stringResource(R.string.duplicate_run_nothing),
        RunMode.Action to stringResource(R.string.duplicate_run_action),
        RunMode.Command to stringResource(R.string.duplicate_run_command)
    )
    ListSection(
        header = stringResource(R.string.duplicate_run_header),
        headerStyle = ListHeaderStyle.Title,
        footer = if (options.runMode !=
            RunMode.None
        ) {
            stringResource(R.string.duplicate_run_footer)
        } else {
            null
        }
    ) {
        SegmentedControl(
            options = modes.map { labels.getValue(it) },
            selectedIndex = modes.indexOf(options.runMode).coerceAtLeast(0),
            onSelect = { onChange(options.withRunMode(modes[it], project)) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )
        if (options.runMode == RunMode.Action) {
            val actions = project.runnableActions
            PickerRow(
                title = stringResource(R.string.duplicate_run_action),
                options = actions.map { it.name },
                selected = options.actionName,
                onSelect = { onChange(options.copy(actionName = it)) },
                optionLabel = { name ->
                    val action = actions.firstOrNull { it.name == name }
                    when {
                        action == null -> name
                        action.emoji.isEmpty() -> action.label
                        else -> "${action.emoji} ${action.label}"
                    }
                }
            )
        }
        if (options.runMode == RunMode.Command) {
            TextFieldRow(
                value = options.command,
                onValueChange = { onChange(options.copy(command = it)) },
                placeholder = stringResource(R.string.duplicate_run_command),
                textStyle = LpmTheme.typography.body.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = PlainKeyboard,
                maxLines = 4
            )
        }
        if (options.runMode != RunMode.None) {
            TextFieldRow(
                value = options.prompt,
                onValueChange = { onChange(options.copy(prompt = it)) },
                placeholder = stringResource(R.string.duplicate_prompt),
                maxLines = 4
            )
        }
    }
}
