package cx.lpm.mobile.feature.automations.conversation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.lpm.mobile.core.designsystem.component.AlertButton
import cx.lpm.mobile.core.designsystem.component.AlertRole
import cx.lpm.mobile.core.designsystem.component.LpmAlert
import cx.lpm.mobile.core.navigation.AutomationRunKey
import cx.lpm.mobile.feature.automations.AutomationsSlots
import cx.lpm.mobile.feature.automations.FollowupComposer
import cx.lpm.mobile.feature.automations.R
import cx.lpm.mobile.feature.automations.ReadAloudRequest
import cx.lpm.mobile.feature.automations.ui.LiveOutputPolling

/** A run's conversation wired to its ViewModel, the composer and the reader. */
@Composable
fun AutomationConversationRoute(
    key: AutomationRunKey,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    slots: AutomationsSlots = AutomationsSlots(),
    viewModel: AutomationConversationViewModel =
        hiltViewModel<AutomationConversationViewModel, AutomationConversationViewModel.Factory>(
            creationCallback = { it.create(key) }
        )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(key) { viewModel.load() }
    LiveOutputPolling(running = state.running, poll = viewModel::loadLiveOutput)
    val title = state.job?.displayName ?: stringResource(R.string.detail_title)
    val placeholder = stringResource(
        if (state.sending) R.string.chat_running_placeholder else R.string.chat_reply_placeholder
    )

    val sourceId = { message: MessageUi -> "${key.project}\n${key.jobId}:${message.at}" }

    AutomationConversationScreen(
        state = state,
        onBack = onBack,
        modifier = modifier,
        speechBar = slots.speechBar,
        speakingBlock = { message -> slots.speakingBlock(sourceId(message)) },
        readAloud = { message ->
            slots.readAloudButton(
                ReadAloudRequest(
                    sourceId = sourceId(message),
                    title = title,
                    subtitle = key.project,
                    markdown = message.output,
                    session = key.session
                )
            )
        },
        composer = {
            slots.followupComposer(
                FollowupComposer(
                    session = key.session,
                    composerId = "automation:${key.project}:${key.jobId}:${key.rootAt}",
                    project = key.project,
                    label = state.job?.displayName ?: key.jobId,
                    disabled = state.sending,
                    placeholder = placeholder,
                    onSend = viewModel::send
                )
            )
        }
    )

    state.followupError?.let { error ->
        LpmAlert(
            title = stringResource(R.string.chat_send_failed),
            message = error,
            buttons = listOf(AlertButton(stringResource(R.string.ok), AlertRole.Cancel)),
            onDismissRequest = viewModel::dismissFollowupError
        )
    }
}
