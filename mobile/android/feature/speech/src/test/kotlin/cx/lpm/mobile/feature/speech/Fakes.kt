package cx.lpm.mobile.feature.speech

import cx.lpm.mobile.core.data.prefs.AppPreferences
import cx.lpm.mobile.core.testing.InMemoryDataStore
import cx.lpm.mobile.feature.speech.data.SpeechStore
import cx.lpm.mobile.feature.speech.engine.AudioSession
import cx.lpm.mobile.feature.speech.engine.ClipPlayer
import cx.lpm.mobile.feature.speech.engine.SpeechEngine
import cx.lpm.mobile.feature.speech.engine.SpeechVoice
import cx.lpm.mobile.feature.speech.engine.Utterance
import cx.lpm.mobile.feature.speech.playback.NowPlaying
import cx.lpm.mobile.feature.speech.playback.NowPlayingInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope

/** An engine the test drives: [queue] is what it was last told to say. */
class FakeSpeechEngine : SpeechEngine {
    override val voices = MutableStateFlow<List<SpeechVoice>>(emptyList())
    override var listener: SpeechEngine.Listener? = null
    var queue: List<Utterance> = emptyList()
    var prepared = 0
    var stops = 0

    override fun prepare() {
        prepared++
    }

    override fun speak(utterances: List<Utterance>) {
        queue = utterances
    }

    override fun stop() {
        stops++
        queue = emptyList()
    }

    fun start(i: Int) = listener!!.onStart(queue[i].id)

    fun range(i: Int, start: Int, end: Int) = listener!!.onRange(queue[i].id, start, end)

    fun done(i: Int) = listener!!.onDone(queue[i].id)
}

class FakeClip(private val onFinish: () -> Unit) : ClipPlayer {
    override var duration = 60.0
    override var currentTime = 0.0
    var loaded: ByteArray? = null
    var speed = 0.0
    var playing = false
    var stopped = false
    var playable = true

    override fun load(audio: ByteArray, rate: Double): Boolean {
        loaded = audio
        speed = rate
        return playable
    }

    override fun play() {
        playing = true
    }

    override fun pause() {
        playing = false
    }

    override fun stop() {
        stopped = true
        playing = false
    }

    override fun seekTo(seconds: Double) {
        currentTime = seconds.coerceIn(0.0, duration - 0.05)
    }

    override fun setRate(rate: Double) {
        speed = rate
    }

    fun finish() = onFinish()
}

class FakeClips : ClipPlayer.Factory {
    val made = mutableListOf<FakeClip>()
    var playable = true

    override fun create(onFinish: () -> Unit): ClipPlayer = FakeClip(onFinish).also {
        it.playable = playable
        made += it
    }
}

class FakeAudioSession : AudioSession {
    override var listener: AudioSession.Listener? = null
    var active = false

    override fun activate() {
        active = true
    }

    override fun deactivate() {
        active = false
    }
}

class FakeNowPlaying : NowPlaying {
    var info: NowPlayingInfo? = null

    override fun update(title: String, subtitle: String, playing: Boolean, seekable: Boolean) {
        info = NowPlayingInfo(title, subtitle, playing, seekable)
    }

    override fun clear() {
        info = null
    }
}

/** A [SpeechStore] over fakes, its ticker on the test's virtual clock. */
class SpeechFixture(
    test: TestScope,
    val prefs: AppPreferences = AppPreferences(InMemoryDataStore(), test.backgroundScope)
) {
    val engine = FakeSpeechEngine()
    val clips = FakeClips()
    val audio = FakeAudioSession()
    val nowPlaying = FakeNowPlaying()
    val store = SpeechStore(engine, clips, audio, nowPlaying, prefs, test.backgroundScope)
    val requests = mutableListOf<Pair<String, String>>()

    fun toggle(markdown: String, sourceId: String = "job:1", title: String = "Nightly tests") =
        store.toggle(sourceId, title, "storefront", markdown) { reqId, text ->
            requests +=
                reqId to text
        }
}
