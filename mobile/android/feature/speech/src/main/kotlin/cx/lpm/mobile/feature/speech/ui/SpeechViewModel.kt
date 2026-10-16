package cx.lpm.mobile.feature.speech.ui

import androidx.lifecycle.ViewModel
import cx.lpm.mobile.core.data.session.MacSessions
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.feature.speech.data.ReadAloudResult
import cx.lpm.mobile.feature.speech.data.SpeechLink
import cx.lpm.mobile.feature.speech.data.SpeechState
import cx.lpm.mobile.feature.speech.data.SpeechStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * The reader as a screen sees it: the bar, each message's button and its highlight
 * share one instance per screen and follow the app-wide [SpeechStore].
 */
@HiltViewModel
class SpeechViewModel @Inject constructor(
    private val speech: SpeechStore,
    private val sessions: MacSessions
) : ViewModel() {
    val uiState: StateFlow<SpeechState> = speech.state

    /** Read [markdown], or stop it; the Mac engine asks [session]'s Mac for the audio. */
    fun toggle(
        sourceId: String,
        title: String,
        subtitle: String,
        markdown: String,
        session: SessionKey
    ): ReadAloudResult = speech.toggle(sourceId, title, subtitle, markdown) { reqId, text ->
        sessions[session]?.store<SpeechLink>()?.request(reqId, text)
    }

    fun togglePause() = speech.togglePause()

    fun skip(delta: Int) = speech.skip(delta)

    fun seek(ratio: Double) = speech.seek(ratio * uiState.value.duration)

    fun setRate(rate: Double) = speech.setRate(rate)

    fun stop() = speech.stop()

    fun dismissError() = speech.dismissError()
}
