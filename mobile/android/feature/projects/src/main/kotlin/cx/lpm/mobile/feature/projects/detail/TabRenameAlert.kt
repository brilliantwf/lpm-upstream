package cx.lpm.mobile.feature.projects.detail

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import cx.lpm.mobile.core.designsystem.component.AlertButton
import cx.lpm.mobile.core.designsystem.component.AlertRole
import cx.lpm.mobile.core.designsystem.component.AlertTextField
import cx.lpm.mobile.core.designsystem.component.LpmAlert
import cx.lpm.mobile.core.model.TerminalInfo
import cx.lpm.mobile.core.model.trimmingWhitespace
import cx.lpm.mobile.feature.projects.R

/** The name to give [term] from what was typed, or null when there's nothing to rename. */
fun tabRenameResult(term: TerminalInfo, text: String): String? =
    text.trimmingWhitespace().takeIf { it.isNotEmpty() && it != term.label }

/** The prompt for a tab's new name, seeded with its current one. */
@Composable
fun TabRenameAlert(term: TerminalInfo, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember(term.id) { mutableStateOf(term.label) }
    LpmAlert(
        title = stringResource(R.string.tab_rename_title),
        textField = {
            AlertTextField(
                name,
                { name = it },
                stringResource(R.string.tab_rename_field),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false
                )
            )
        },
        buttons = listOf(
            AlertButton(stringResource(R.string.cancel), AlertRole.Cancel),
            AlertButton(stringResource(R.string.tab_rename)) {
                tabRenameResult(term, name)?.let(onRename)
            }
        ),
        onDismissRequest = onDismiss
    )
}
