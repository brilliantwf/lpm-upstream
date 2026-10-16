package cx.lpm.mobile.feature.speech.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.lpm.mobile.core.designsystem.component.AlertButton
import cx.lpm.mobile.core.designsystem.component.AlertRole
import cx.lpm.mobile.core.designsystem.component.LpmAlert
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.speech.R
import cx.lpm.mobile.feature.speech.data.SpeechError

/**
 * The reader under a screen's navigation bar while it speaks, not over the composer:
 * it stays put while the conversation scrolls, and the reply field keeps the thumb
 * zone. The band takes the screen's own background so scrolled text doesn't show
 * around the capsule. Also raises "Couldn't read aloud".
 */
@Composable
fun SpeechBarRoute(modifier: Modifier = Modifier, viewModel: SpeechViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AnimatedVisibility(
        visible = state.isActive,
        modifier = modifier,
        enter = slideInVertically(tween(DURATION)) { -it } +
            expandVertically(tween(DURATION), expandFrom = Alignment.Top) + fadeIn(tween(DURATION)),
        exit = slideOutVertically(tween(DURATION)) { -it } +
            shrinkVertically(tween(DURATION), shrinkTowards = Alignment.Top) +
            fadeOut(tween(DURATION))
    ) {
        SpeechBar(
            state = state,
            onTogglePause = viewModel::togglePause,
            onSkip = viewModel::skip,
            onSeek = viewModel::seek,
            onRateChange = viewModel::setRate,
            onStop = viewModel::stop,
            modifier = Modifier
                .fillMaxWidth()
                .background(LpmTheme.colors.systemGroupedBackground)
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp)
        )
    }
    state.error?.let { error ->
        LpmAlert(
            title = stringResource(R.string.speech_error_title),
            message = when (error) {
                is SpeechError.Mac -> error.message
                SpeechError.Synthesis -> stringResource(R.string.speech_error_synthesis)
                SpeechError.Playback -> stringResource(R.string.speech_error_playback)
            },
            buttons = listOf(AlertButton(stringResource(R.string.speech_ok), AlertRole.Cancel)),
            onDismissRequest = viewModel::dismissError
        )
    }
}

private const val DURATION = 200
