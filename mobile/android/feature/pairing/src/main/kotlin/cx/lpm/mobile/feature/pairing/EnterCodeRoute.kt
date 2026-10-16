package cx.lpm.mobile.feature.pairing

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Enter a Code with its ViewModel, opened with `host` and `port` filled in. */
@Composable
fun EnterCodeRoute(
    host: String,
    port: Int,
    onBack: () -> Unit,
    issueCard: IssueCardSlot,
    modifier: Modifier = Modifier,
    insets: WindowInsets? = null,
    viewModel: EnterCodeViewModel =
        hiltViewModel<EnterCodeViewModel, EnterCodeViewModel.Factory>(key = "code-$host:$port") {
            it.create(host, port)
        }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    EnterCodeScreen(
        form = viewModel.form,
        state = state,
        onFormChange = { viewModel.form = it },
        onPair = { viewModel.pair() },
        onIssueAction = viewModel::handle,
        onBack = onBack,
        issueCard = issueCard,
        insets = insets,
        modifier = modifier
    )
}
