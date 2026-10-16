package cx.lpm.mobile.feature.speech.engine

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [SpeechEngine] over the phone's `TextToSpeech`, connected on first use. Text longer
 * than the engine takes in one call is spoken in chunks that report as one utterance.
 */
@Singleton
class AndroidSpeechEngine @Inject constructor(
    @param:ApplicationContext private val context: Context
) : SpeechEngine {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: List<Utterance>? = null
    private var engineVoices: Map<String, Voice> = emptyMap()

    /** Chunk id → its utterance, where in that text it starts, and whether it's the last. */
    private val chunks = mutableMapOf<String, Chunk>()

    private val mutableVoices = MutableStateFlow<List<SpeechVoice>>(emptyList())
    override val voices: StateFlow<List<SpeechVoice>> = mutableVoices.asStateFlow()

    override var listener: SpeechEngine.Listener? = null

    override fun prepare() {
        if (tts == null) {
            tts = TextToSpeech(context) { status -> main.post { onInit(status) } }
        } else if (ready) {
            refreshVoices()
        }
    }

    override fun speak(utterances: List<Utterance>) {
        if (!ready) {
            pending = utterances
            prepare()
            return
        }
        enqueue(utterances)
    }

    override fun stop() {
        pending = null
        chunks.clear()
        tts?.stop()
    }

    private fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) {
            engine.shutdown()
            tts = null
            if (pending != null) {
                pending = null
                listener?.onUnavailable()
            }
            return
        }
        ready = true
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        engine.setOnUtteranceProgressListener(progress)
        refreshVoices()
        pending?.let {
            pending = null
            enqueue(it)
        }
    }

    private fun refreshVoices() {
        val engine = tts ?: return
        val all = runCatching { engine.voices.orEmpty() }.getOrDefault(emptySet())
        val default = runCatching { engine.defaultVoice?.name }.getOrNull()
        engineVoices = all.associateBy { it.name }
        mutableVoices.value =
            SpeechVoices.installed(all.map(::engineVoice), engineDefault = default)
    }

    private fun enqueue(utterances: List<Utterance>) {
        val engine = tts ?: return
        chunks.clear()
        utterances.firstOrNull()?.let { useVoice(engine, it.voiceId) }
        var mode = TextToSpeech.QUEUE_FLUSH
        for (utterance in utterances) {
            engine.setSpeechRate(utterance.rate)
            val parts = split(utterance.text, TextToSpeech.getMaxSpeechInputLength() - 1)
            parts.forEachIndexed { i, (offset, text) ->
                val id = if (i == 0) utterance.id else "${utterance.id}$CHUNK$i"
                chunks[id] =
                    Chunk(utterance.id, offset, first = i == 0, last = i == parts.lastIndex)
                engine.speak(text, mode, Bundle.EMPTY, id)
                mode = TextToSpeech.QUEUE_ADD
            }
            if (utterance.pauseAfterMs > 0) {
                engine.playSilentUtterance(utterance.pauseAfterMs, mode, "${utterance.id}$PAUSE")
            }
        }
    }

    /** The saved voice if it's still offered, else the best one, else the engine's choice. */
    private fun useVoice(engine: TextToSpeech, voiceId: String) {
        val offered = mutableVoices.value
        val id = offered.firstOrNull { it.id == voiceId }?.id ?: offered.firstOrNull()?.id
        val voice = id?.let(engineVoices::get)
        if (voice != null) engine.voice = voice else engine.language = Locale.getDefault()
    }

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) = post(utteranceId) { chunk ->
            if (chunk.first) listener?.onStart(chunk.utterance)
        }

        override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) =
            post(utteranceId) { chunk ->
                listener?.onRange(chunk.utterance, chunk.offset + start, chunk.offset + end)
            }

        override fun onDone(utteranceId: String) = post(utteranceId) { chunk ->
            if (chunk.last) listener?.onDone(chunk.utterance)
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) = onDone(utteranceId)

        override fun onError(utteranceId: String, errorCode: Int) = onDone(utteranceId)

        private fun post(utteranceId: String, block: (Chunk) -> Unit) {
            main.post { chunks[utteranceId]?.let(block) }
        }
    }

    private data class Chunk(
        val utterance: String,
        val offset: Int,
        val first: Boolean,
        val last: Boolean
    )

    private companion object {
        const val CHUNK = "#chunk"
        const val PAUSE = "#pause"

        fun engineVoice(voice: Voice) = EngineVoice(
            name = voice.name,
            locale = voice.locale,
            quality = voice.quality,
            needsNetwork = voice.isNetworkConnectionRequired,
            notInstalled = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED in voice.features.orEmpty()
        )

        /** [text] in pieces of at most [max] characters, cut at spaces, with where each starts. */
        fun split(text: String, max: Int): List<Pair<Int, String>> {
            if (text.length <= max) return listOf(0 to text)
            val out = mutableListOf<Pair<Int, String>>()
            var start = 0
            while (start < text.length) {
                var end = minOf(text.length, start + max)
                if (end < text.length) {
                    val space = text.lastIndexOf(' ', end)
                    if (space > start) end = space + 1
                }
                out += start to text.substring(start, end)
                start = end
            }
            return out
        }
    }
}
