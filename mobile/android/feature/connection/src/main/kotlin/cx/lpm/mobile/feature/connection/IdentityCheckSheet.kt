package cx.lpm.mobile.feature.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ModalSheet
import cx.lpm.mobile.core.designsystem.component.PrimaryButton
import cx.lpm.mobile.core.designsystem.component.SecondaryButton
import cx.lpm.mobile.core.designsystem.component.rememberModalSheetState
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.SessionKey

/**
 * Shown when the active Mac presents a different security identity than the one
 * this phone paired with. Instead of trusting it on a warning, the user compares a
 * short code with the one lpm shows on the Mac.
 */
@Composable
internal fun IdentityCheckSheet(session: SessionKey, onDismiss: () -> Unit) {
    val sheet = rememberModalSheetState()
    val viewModel = hiltViewModel<IdentityCheckViewModel, IdentityCheckViewModel.Factory>(
        key = "identity-$session"
    ) { it.create(session) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    ModalSheet(onDismissRequest = onDismiss, state = sheet, plain = true) {
        IdentityCheckContent(
            state = state,
            onMatch = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                viewModel.trust()
                sheet.dismiss(onDismiss)
            },
            onPairAgain = {
                viewModel.pairAgainAfterClose()
                sheet.dismiss(onDismiss)
            },
            onMismatch = {
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                viewModel.reject()
                sheet.dismiss(onDismiss)
            }
        )
    }
}

@Composable
internal fun IdentityCheckContent(
    state: IdentityCheckUiState,
    onMatch: () -> Unit,
    onPairAgain: () -> Unit,
    onMismatch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LpmTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Box(modifier.fillMaxSize()) {
        DragIndicator(Modifier.align(Alignment.TopCenter))
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(DesignR.drawable.ic_gpp_maybe),
                contentDescription = null,
                tint = colors.orange,
                modifier = Modifier.padding(top = 8.dp).size(52.dp)
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.identity_title, state.macLabel),
                    style = LpmTheme.typography.title2.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
                Text(
                    stringResource(R.string.identity_message),
                    style = LpmTheme.typography.callout,
                    color = colors.secondaryLabel,
                    textAlign = TextAlign.Center
                )
            }
            state.code?.let { code -> CodeBox(code) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.code != null) {
                    PrimaryButton(
                        stringResource(R.string.identity_match),
                        onMatch,
                        shape = shape,
                        modifier = Modifier.heightIn(min = 60.dp)
                    )
                }
                SecondaryButton(
                    stringResource(R.string.identity_pair_again),
                    onPairAgain,
                    shape = shape,
                    textStyle = LpmTheme.typography.body
                )
                if (state.code != null) {
                    SecondaryButton(
                        stringResource(R.string.identity_mismatch),
                        onMismatch,
                        shape = shape,
                        contentColor = colors.red,
                        textStyle = LpmTheme.typography.body
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBox(code: String) {
    val label = stringResource(R.string.identity_code_label, code.replace(" · ", " "))
    BasicText(
        text = code,
        style = LpmTheme.typography.title1.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            fontFeatureSettings = "tnum",
            textAlign = TextAlign.Center,
            color = LpmTheme.colors.label
        ),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = 30.sp),
        modifier = Modifier
            .fillMaxWidth()
            .background(LpmTheme.colors.tertiarySystemGroupedBackground, RoundedCornerShape(18.dp))
            .padding(vertical = 14.dp)
            .clearAndSetSemantics { contentDescription = label }
    )
}

/** The grabber a sheet shows with `.presentationDragIndicator(.visible)`. */
@Composable
internal fun DragIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(top = 5.dp)
            .size(width = 36.dp, height = 5.dp)
            .background(LpmTheme.colors.tertiaryLabel, CircleShape)
    )
}
