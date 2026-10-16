package cx.lpm.mobile.feature.pairing

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.lpm.mobile.core.designsystem.component.GlassCapsuleButton
import cx.lpm.mobile.core.designsystem.component.LpmTopBar
import cx.lpm.mobile.core.designsystem.component.ModalSheet
import cx.lpm.mobile.core.designsystem.component.rememberModalSheetState
import cx.lpm.mobile.core.navigation.SheetStack
import cx.lpm.mobile.core.navigation.rememberSheetNavigator

private sealed interface AddMacPage {
    data object Pairing : AddMacPage

    data class EnterCode(val target: CodeTarget) : AddMacPage
}

/**
 * "Add a machine" (or pairing a saved Mac again) over the projects list, while the
 * session asks for it. The app puts this over the projects root.
 */
@Composable
fun AddMacSheetHost(issueCard: IssueCardSlot, viewModel: AddMacViewModel = hiltViewModel()) {
    val adding by viewModel.adding.collectAsStateWithLifecycle()
    if (adding) AddMacSheet(onDismiss = viewModel::close, issueCard = issueCard)
}

/** The pairing screen in a sheet with Cancel, Enter a Code pushed inside it. */
@Composable
internal fun AddMacSheet(onDismiss: () -> Unit, issueCard: IssueCardSlot) {
    val sheet = rememberModalSheetState()
    val navigator = rememberSheetNavigator<AddMacPage>(AddMacPage.Pairing)
    ModalSheet(onDismissRequest = onDismiss, state = sheet) {
        SheetStack(navigator) { page ->
            when (page) {
                AddMacPage.Pairing -> Column {
                    LpmTopBar(
                        title = "",
                        windowInsets = WindowInsets(0),
                        modifier = Modifier.padding(top = 16.dp),
                        leading = {
                            GlassCapsuleButton(stringResource(R.string.cancel), onClick = {
                                sheet.dismiss(onDismiss)
                            })
                        }
                    )
                    PairingRoute(
                        adding = true,
                        issueCard = issueCard,
                        onEnterCode = { navigator.push(AddMacPage.EnterCode(it)) }
                    )
                }

                is AddMacPage.EnterCode -> EnterCodeRoute(
                    host = page.target.host,
                    port = page.target.port,
                    onBack = navigator::back,
                    issueCard = issueCard
                )
            }
        }
    }
}
