package cx.lpm.mobile.feature.speech.data

import cx.lpm.mobile.core.data.di.ApplicationScope
import cx.lpm.mobile.core.data.prefs.AppPreferences
import cx.lpm.mobile.core.data.prefs.SpeechPrefs
import cx.lpm.mobile.core.data.prefs.setSpeechRate
import cx.lpm.mobile.core.data.prefs.speechPrefs
import cx.lpm.mobile.feature.speech.data.SpeechState.Mode
import cx.lpm.mobile.feature.speech.engine.AudioSession
import cx.lpm.mobile.feature.speech.engine.ClipPlayer
import cx.lpm.mobile.feature.speech.engine.SpeechEngine
import cx.lpm.mobile.feature.speech.engine.Utterance
import cx.lpm.mobile.feature.speech.playback.NowPlaying
import cx.lpm.mobile.feature.speech.text.SpeechText
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Reads an automation result aloud (iOS `SpeechStore`). App-wide rather than in a
 * screen, so playback survives leaving the chat — the point of the feature is
 * listening to a digest with the screen off.
 *
 * Text is spoken one segment at a time: that is what gives progress to report and
 * lets skip jump by sentence. The phone's voice has no timeline, so skip moves
 * between segments and progress counts characters spoken; it has no pause either,
 * so pausing stops and resuming starts again at the word it was on. The Mac's clip
 * ([ClipPlayer]) has a real timeline instead.
 */
@Singleton
class SpeechStore @Inject constructor(
    private val engine: SpeechEngine,
    private val clips: ClipPlayer.Factory,
    private val audio: AudioSession,
    private val nowPlaying: NowPlaying,
    private val prefs: AppPreferences,
    @param:ApplicationScope private val scope: CoroutineScope
) : SpeechEngine.Listener,
    AudioSession.Listener {
    private val mutableState = MutableStateFlow(SpeechState(rate = prefs.speechPrefs.rate))
    val state: StateFlow<SpeechState> = mutableState.asStateFlow()

    private var clip: ClipPlayer? = null
    private var pendingReqId: String? = null
    private val progress = SegmentProgress()
    private var ticker: Job? = null

    /**
     * Bumped on every stop or restart. Callbacks for a superseded run carry an old
     * generation and are ignored, so a cancel never clobbers the run that replaced it.
     */
    private var generation = 0
    private val utterances = mutableMapOf<String, Int>()

    /** The in-flight Settings voice sample, which belongs to no reading session. */
    private var previewId: String? = null

    init {
        engine.listener = this
        audio.listener = this
    }

    /**
     * Start reading [markdown], or stop if this source is already being read.
     * [requestSpeech] sends the Mac engine's synthesis request to the source's Mac.
     */
    fun toggle(
        sourceId: String,
        title: String,
        subtitle: String,
        markdown: String,
        requestSpeech: (reqId: String, text: String) -> Unit
    ): ReadAloudResult {
        if (state.value.sourceId == sourceId && state.value.isActive) {
            stop()
            return ReadAloudResult.Stopped
        }
        val segments = SpeechText.segments(markdown)
        if (segments.isEmpty()) return ReadAloudResult.NothingToRead
        stop()
        val prefs = prefs.speechPrefs
        // Settings can have moved the speed since the last reading.
        mutableState.update {
            it.copy(rate = prefs.rate, title = title, subtitle = subtitle, sourceId = sourceId)
        }
        progress.reset(segments)

        if (prefs.engine == SpeechPrefs.ENGINE_MAC) {
            // The Mac renders the whole document in one clip, so nothing reports
            // per-word timing. The segments still map a playback position back to a
            // block by character offset — approximate, but rarely off by a paragraph.
            val reqId = UUID.randomUUID().toString()
            pendingReqId = reqId
            mutableState.update { it.copy(isLoading = true, mode = Mode.Speaking) }
            updateNowPlaying()
            requestSpeech(reqId, segments.joinToString(" ") { it.text })
            return ReadAloudResult.Started
        }
        audio.activate()
        speak(from = 0)
        startTicker()
        return ReadAloudResult.Started
    }

    /**
     * Speak a short sample so a voice can be auditioned before it is chosen. Outside
     * the reading state machine, so the reader bar doesn't flash up for it.
     */
    fun preview(voiceId: String) {
        stop()
        val id = "preview-${++generation}"
        previewId = id
        audio.activate()
        engine.speak(
            listOf(Utterance(id, PREVIEW_TEXT, voiceId, utteranceRate(prefs.speechPrefs.rate)))
        )
    }

    /** The Mac's answer to a request; replies to a superseded one are dropped. */
    fun receiveSpeech(reqId: String, audio: String?, error: String?) {
        if (reqId != pendingReqId) return
        pendingReqId = null
        mutableState.update { it.copy(isLoading = false) }
        val data = audio?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
        if (data == null) {
            fail(error?.let(SpeechError::Mac) ?: SpeechError.Synthesis)
            return
        }
        lateinit var player: ClipPlayer
        player = clips.create(onFinish = { if (clip === player) stop() })
        if (!player.load(data, prefs.speechPrefs.rate)) {
            fail(SpeechError.Playback)
            return
        }
        this.audio.activate()
        clip = player
        mutableState.update { it.copy(duration = player.duration) }
        player.play()
        mutableState.update { it.copy(mode = Mode.Speaking) }
        startTicker()
        updateNowPlaying()
    }

    fun stop() {
        generation++
        utterances.clear()
        previewId = null
        ticker?.cancel()
        ticker = null
        engine.stop()
        clip?.stop()
        clip = null
        pendingReqId = null
        progress.reset(emptyList())
        mutableState.update { SpeechState(rate = it.rate, error = it.error) }
        nowPlaying.clear()
        audio.deactivate()
    }

    fun togglePause() {
        when (state.value.mode) {
            Mode.Speaking -> pause()
            Mode.Paused -> resume()
            Mode.Idle -> Unit
        }
    }

    /**
     * Move by whole segments; a clip moves by 15 seconds. Going back mid-sentence
     * restarts the current one first, which is what "previous" means everywhere else.
     */
    fun skip(delta: Int) {
        clip?.let {
            it.seekBy(delta * SKIP_SECONDS)
            refreshClipProgress()
            return
        }
        if (!state.value.isActive || progress.isEmpty || pendingReqId != null) return
        var target = progress.index + delta
        if (delta < 0 && state.value.elapsed - progress.elapsedAtSegmentStart > RESTART_SECONDS) {
            target = progress.index
        }
        restart(from = target.coerceIn(0, progress.lastIndex))
    }

    /** Scrub to a position; only a clip has one. */
    fun seek(seconds: Double) {
        val player = clip ?: return
        player.seekTo(seconds)
        refreshClipProgress()
    }

    fun setRate(rate: Double) {
        prefs.setSpeechRate(rate)
        mutableState.update { it.copy(rate = rate) }
        if (!state.value.isActive) return
        // A clip changes speed in place; the voice fixes its rate per utterance, so
        // it re-queues from the current segment. A clip still rendering picks the new
        // speed up when it loads.
        clip?.let {
            it.setRate(rate)
            return
        }
        if (pendingReqId == null) restart(from = progress.index)
    }

    fun dismissError() = mutableState.update { it.copy(error = null) }

    fun pause() {
        if (state.value.mode != Mode.Speaking) return
        val player = clip
        if (player != null) {
            player.pause()
        } else {
            generation++
            utterances.clear()
            engine.stop()
        }
        ticker?.cancel()
        ticker = null
        mutableState.update { it.copy(mode = Mode.Paused) }
        updateNowPlaying()
    }

    fun resume() {
        if (state.value.mode != Mode.Paused) return
        audio.activate()
        val player = clip
        when {
            player != null -> player.play()
            pendingReqId == null -> speak(from = progress.index, offset = progress.wordStart)
        }
        mutableState.update { it.copy(mode = Mode.Speaking) }
        startTicker()
        updateNowPlaying()
    }

    /** Speak from [from] again, playing if paused. */
    private fun restart(from: Int) {
        speak(from)
        startTicker()
    }

    /**
     * Queue every remaining segment at once and let the engine run them back to back;
     * a jump re-queues from the new position. [offset] resumes inside the first one.
     */
    private fun speak(from: Int, offset: Int = 0) {
        generation++
        utterances.clear()
        progress.start(from, offset, state.value.elapsed)
        publishProgress()
        val prefs = prefs.speechPrefs
        val rate = utteranceRate(prefs.rate)
        val queue = (from..progress.lastIndex).map { i ->
            val segment = progress.segments[i]
            val text = if (i == from) segment.text.substring(progress.offset) else segment.text
            Utterance("s$generation-$i", text, prefs.voiceId, rate, segment.pauseMs).also {
                utterances[it.id] = i
            }
        }
        engine.speak(queue)
        mutableState.update { it.copy(mode = Mode.Speaking) }
        updateNowPlaying()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                if (state.value.mode != Mode.Speaking) continue
                if (clip != null) {
                    refreshClipProgress()
                } else {
                    mutableState.update { it.copy(elapsed = it.elapsed + TICK_MS / 1000.0) }
                }
            }
        }
    }

    private fun refreshClipProgress() {
        val player = clip ?: return
        val fraction = if (player.duration >
            0
        ) {
            minOf(1.0, player.currentTime / player.duration)
        } else {
            0.0
        }
        mutableState.update {
            it.copy(
                elapsed = player.currentTime,
                duration = player.duration,
                progress = fraction,
                speakingBlock = progress.blockAt(fraction)
            )
        }
    }

    private fun publishProgress() = mutableState.update {
        it.copy(progress = progress.fraction, speakingBlock = progress.block)
    }

    private fun fail(error: SpeechError) {
        stop()
        mutableState.update { it.copy(error = error) }
    }

    private fun updateNowPlaying() {
        val s = state.value
        nowPlaying.update(
            s.title,
            s.subtitle,
            playing = s.mode == Mode.Speaking,
            seekable = s.canSeek
        )
    }

    // Engine callbacks

    override fun onStart(utteranceId: String) {
        val index = utterances[utteranceId] ?: return
        progress.started(index, state.value.elapsed)
        publishProgress()
    }

    override fun onRange(utteranceId: String, start: Int, end: Int) {
        val index = utterances[utteranceId] ?: return
        progress.spoke(index, start, end)
        publishProgress()
    }

    override fun onDone(utteranceId: String) {
        if (utteranceId == previewId) {
            previewId = null
            if (!state.value.isActive) audio.deactivate()
            return
        }
        if (utterances[utteranceId] == progress.lastIndex) stop()
    }

    override fun onUnavailable() {
        if (state.value.isActive) fail(SpeechError.Synthesis) else stop()
    }

    // Audio session callbacks

    override fun onInterruptionBegan() = pause()

    override fun onInterruptionEnded() = resume()

    override fun onRouteLost() = pause()

    private companion object {
        const val PREVIEW_TEXT = "This is how your automation replies will sound."
        const val TICK_MS = 500L
        const val SKIP_SECONDS = 15.0
        const val RESTART_SECONDS = 3.0

        /** The phone's normal rate is 1, as iOS's default rate is its normal one. */
        fun utteranceRate(multiplier: Double): Float = multiplier.toFloat()
    }
}
