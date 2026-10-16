package cx.lpm.mobile.feature.connection

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.ListHeaderStyle
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.component.TextFieldRow
import cx.lpm.mobile.core.model.SessionKey

/**
 * The addresses and port the phone uses to reach a saved Mac. Each says what it is
 * and whether it answers from here; the phone tries them all and connects to
 * whichever answers. Edit removes and reorders them.
 */
@Composable
internal fun EditEndpointPage(
    macId: String,
    session: SessionKey,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditEndpointViewModel =
        hiltViewModel<EditEndpointViewModel, EditEndpointViewModel.Factory>(
            key = "endpoint-$macId"
        ) {
            it.create(session, macId)
        }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    SheetPage(
        title = stringResource(R.string.addresses),
        modifier = modifier,
        onBack = onBack,
        trailing = {
            ToolbarTextButton(
                text = stringResource(if (editing) R.string.done else R.string.edit),
                onClick = { editing = !editing },
                bold = editing
            )
            ToolbarTextButton(
                text = stringResource(R.string.save),
                enabled = viewModel.canSave,
                bold = true,
                onClick = {
                    viewModel.save()
                    onBack()
                }
            )
        }
    ) {
        SheetForm(top = FormTopWithHeader) {
            val find = when (val result = viewModel.findResult) {
                is FindResult.Found -> stringResource(R.string.endpoint_found, result.host) + "\n"
                FindResult.NotFound -> stringResource(R.string.endpoint_not_found) + "\n"
                null -> ""
            }
            ListSection(
                header = stringResource(
                    R.string.endpoint_ways,
                    state.record?.displayName ?: stringResource(R.string.endpoint_it)
                ),
                headerStyle = ListHeaderStyle.Title,
                footer = find + stringResource(R.string.endpoint_footer)
            ) {
                viewModel.hosts.forEachIndexed { index, field ->
                    AddressField(
                        field = field,
                        index = index,
                        editing = editing,
                        inUse = state.inUse != null && field.host == state.inUse,
                        testing = field.host in state.testing,
                        check = state.checks[field.host],
                        onChange = { viewModel.setHost(field.id, it) },
                        onRemove = { viewModel.removeHost(field.id) },
                        onMove = viewModel::moveHost
                    )
                }
                ActionRow(
                    stringResource(R.string.endpoint_test),
                    DesignR.drawable.ic_check_circle,
                    viewModel::test,
                    enabled = viewModel.cleanedHosts.isNotEmpty() &&
                        viewModel.portValue != null &&
                        state.testing.isEmpty()
                )
                if (viewModel.canFind) {
                    FindRow(finding = viewModel.finding, onClick = viewModel::find)
                }
                ActionRow(
                    stringResource(R.string.endpoint_add),
                    DesignR.drawable.ic_add_circle,
                    viewModel::addHost
                )
            }
            ListSection(
                header = stringResource(R.string.port),
                headerStyle = ListHeaderStyle.Title
            ) {
                TextFieldRow(
                    value = viewModel.port,
                    onValueChange = { viewModel.port = it },
                    placeholder = stringResource(R.string.port),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }
    }
}

@Composable
private fun FindRow(finding: Boolean, onClick: () -> Unit) {
    ActionRow(
        text = stringResource(R.string.endpoint_find),
        icon = DesignR.drawable.ic_wifi,
        onClick = onClick,
        enabled = !finding,
        trailing = { if (finding) ActivityIndicator(size = ActivityIndicatorSize.Small) }
    )
}
