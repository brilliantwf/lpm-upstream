package cx.lpm.mobile.feature.speech.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.clickableNoIndication
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.feature.speech.R
import cx.lpm.mobile.feature.speech.data.ReadAloudResult

/**
 * "Read aloud" beside a message's copy button; while that message is being read it
 * becomes "Stop reading". [sourceId] tells the reader which message is playing.
 */
@Composable
fun ReadAloudButton(
    sourceId: String,
    title: String,
    subtitle: String,
    markdown: String,
    modifier: Modifier = Modifier,
    session: SessionKey = SessionKey.Live,
    viewModel: SpeechViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    ReadAloudToggle(
        reading = state.isActive && state.sourceId == sourceId,
        onClick = {
            when (viewModel.toggle(sourceId, title, subtitle, markdown, session)) {
                ReadAloudResult.Started -> haptics.performHapticFeedback(
                    HapticFeedbackType.ContextClick
                )

                ReadAloudResult.NothingToRead -> haptics.performHapticFeedback(
                    HapticFeedbackType.Reject
                )

                ReadAloudResult.Stopped -> Unit
            }
        },
        modifier = modifier
    )
}

@Composable
fun ReadAloudToggle(reading: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 30.dp, height = 28.dp)
            .clickableNoIndication(onClick = onClick),
        contentAlignment = Alignment.CenterStart
    ) {
        Icon(
            painter = painterResource(
                if (reading) DesignR.drawable.ic_stop_circle else DesignR.drawable.ic_volume_up
            ),
            contentDescription = stringResource(
                if (reading) R.string.speech_stop_reading else R.string.speech_read_aloud
            ),
            tint = if (reading) LpmTheme.colors.tint else LpmTheme.colors.secondaryLabel,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** The block of [sourceId]'s message being spoken, for its highlight; null when it isn't. */
@Composable
fun speakingBlockOf(sourceId: String, viewModel: SpeechViewModel = hiltViewModel()): Int? {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    return if (state.isActive && state.sourceId == sourceId) state.speakingBlock else null
}
