package cx.lpm.mobile.feature.speech.data

import cx.lpm.mobile.core.data.client.LpmClient
import cx.lpm.mobile.core.data.client.requestSpeech
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.data.session.SessionStore
import javax.inject.Provider

/**
 * A session's side of reading aloud on the Mac engine (iOS `AppModel.wire`'s
 * `requestSpeech` / `onSpeech`): requests go to this session's Mac, its replies to
 * the one app-wide [SpeechStore].
 */
class SpeechLink(private val session: MacSession, private val speech: Provider<SpeechStore>) :
    SessionStore {
    override fun wire(client: LpmClient) {
        client.onSpeech = { reqId, audio, error -> speech.get().receiveSpeech(reqId, audio, error) }
    }

    override fun reset() = Unit

    fun request(reqId: String, text: String) {
        session.client?.requestSpeech(reqId, text)
    }
}
