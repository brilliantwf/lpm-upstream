package cx.lpm.mobile.feature.connection

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import cx.lpm.mobile.core.data.mac.MacRecord
import cx.lpm.mobile.core.designsystem.component.AlertButton
import cx.lpm.mobile.core.designsystem.component.AlertRole
import cx.lpm.mobile.core.designsystem.component.ButtonRow
import cx.lpm.mobile.core.designsystem.component.ListHeaderStyle
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.component.LpmAlert
import cx.lpm.mobile.core.designsystem.component.NavigationRow
import cx.lpm.mobile.core.designsystem.component.TextFieldRow
import cx.lpm.mobile.core.designsystem.component.ValueRow

/** One saved machine: rename it, edit how the phone reaches it, or remove it. */
@Composable
internal fun MacDetailPage(
    macId: String,
    state: ManageMacsUiState,
    onBack: () -> Unit,
    onAddresses: () -> Unit,
    onRename: (String, String) -> Unit,
    onSwitch: (MacRecord) -> Unit,
    onRemove: () -> Unit
) {
    val record = state.record(macId)
    var name by rememberSaveable { mutableStateOf(record?.customName.orEmpty()) }
    var removing by rememberSaveable { mutableStateOf(false) }
    val latestName by rememberUpdatedState(name)
    val exists by rememberUpdatedState(record != null)
    val rename by rememberUpdatedState(onRename)
    DisposableEffect(macId) {
        onDispose { if (exists) rename(macId, latestName) }
    }
    SheetPage(
        title = record?.displayName ?: stringResource(R.string.machine),
        onBack = onBack
    ) {
        if (record == null) return@SheetPage
        val via = state.via(record)
        SheetForm(top = FormTopWithHeader) {
            ListSection(
                header = stringResource(R.string.name),
                headerStyle = ListHeaderStyle.Title,
                footer = stringResource(R.string.rename_message)
            ) {
                TextFieldRow(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = record.name,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onRename(macId, name) })
                )
            }
            ListSection {
                if (via != null) {
                    ValueRow(stringResource(R.string.machine_opens_through), via.displayName)
                } else {
                    NavigationRow(
                        title = stringResource(R.string.addresses),
                        value = record.hosts.size.toString(),
                        onClick = onAddresses
                    )
                }
                if (record.localId != state.activeMacId) {
                    ButtonRow(stringResource(R.string.machine_switch), { onSwitch(record) })
                }
            }
            ListSection {
                ButtonRow(stringResource(R.string.remove_from_phone), {
                    removing = true
                }, destructive = true)
            }
        }
    }
    if (removing && record != null) {
        val via = state.via(record)
        LpmAlert(
            title = stringResource(R.string.remove_named, record.displayName),
            message = if (via != null) {
                stringResource(R.string.remove_via_message, via.displayName, "")
            } else {
                stringResource(R.string.mac_remove_message, "")
            },
            buttons = listOf(
                AlertButton(
                    stringResource(R.string.remove),
                    AlertRole.Destructive,
                    onClick = onRemove
                ),
                AlertButton(stringResource(R.string.cancel), AlertRole.Cancel)
            ),
            onDismissRequest = { removing = false }
        )
    }
}
