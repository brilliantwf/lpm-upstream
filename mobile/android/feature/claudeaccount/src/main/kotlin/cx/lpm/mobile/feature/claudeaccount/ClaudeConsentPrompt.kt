package cx.lpm.mobile.feature.claudeaccount

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.lpm.mobile.core.designsystem.component.AlertButton
import cx.lpm.mobile.core.designsystem.component.AlertRole
import cx.lpm.mobile.core.designsystem.component.LpmAlert
import cx.lpm.mobile.core.model.SessionKey

/**
 * Asks before accounts join a project's list to take turns on, beside the project's
 * menu: the menu closes when an account is picked, so the question can't live in it.
 */
@Composable
fun ClaudeConsentPromptRoute(
    session: SessionKey,
    project: String,
    viewModel: ClaudeAccountMenuViewModel = claudeAccountMenuViewModel(session, project)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val request = state.consent ?: return
    val haptics = LocalHapticFeedback.current
    ClaudeConsentPrompt(
        request = request,
        onConfirm = {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            viewModel.confirmConsent()
        },
        onDismiss = viewModel::dismissConsent
    )
}

/** The desktop's notice, word for word, after the accounts it asks about. */
@Composable
fun ClaudeConsentPrompt(
    request: ClaudeConsentRequest,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val one = request.accounts.size == 1
    val names = request.accounts.map {
        if (it.email.isEmpty()) {
            it.label
        } else {
            stringResource(R.string.claudeaccount_consent_account, it.label, it.email)
        }
    }
    LpmAlert(
        title = stringResource(
            if (one) {
                R.string.claudeaccount_consent_title_one
            } else {
                R.string.claudeaccount_consent_title_many
            }
        ),
        message = stringResource(R.string.claudeaccount_consent_message, names.joinToString(", ")),
        buttons = listOf(
            AlertButton(
                label = stringResource(
                    if (one) {
                        R.string.claudeaccount_consent_confirm_one
                    } else {
                        R.string.claudeaccount_consent_confirm_many
                    }
                ),
                onClick = onConfirm
            ),
            AlertButton(
                label = stringResource(R.string.claudeaccount_consent_cancel),
                role = AlertRole.Cancel
            )
        ),
        onDismissRequest = onDismiss
    )
}
