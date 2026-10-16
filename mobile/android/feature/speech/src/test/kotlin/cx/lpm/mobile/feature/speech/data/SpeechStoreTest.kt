package cx.lpm.mobile.feature.speech.data

import cx.lpm.mobile.core.data.prefs.SpeechPrefs
import cx.lpm.mobile.core.data.prefs.setSpeechEngine
import cx.lpm.mobile.core.data.prefs.setSpeechRate
import cx.lpm.mobile.core.data.prefs.setSpeechVoiceId
import cx.lpm.mobile.core.data.prefs.speechPrefs
import cx.lpm.mobile.core.testing.settle
import cx.lpm.mobile.feature.speech.SpeechFixture
import cx.lpm.mobile.feature.speech.data.SpeechState.Mode
import cx.lpm.mobile.feature.speech.playback.NowPlayingInfo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpeechStoreTest {
    @Test
    fun `reading queues every segment and shows the reader`() = runTest {
        val f = SpeechFixture(this)
        f.prefs.setSpeechVoiceId("en-us-x-sfg-local")

        assertEquals(ReadAloudResult.Started, f.toggle(DOC))

        val s = f.store.state.value
        assertEquals(Mode.Speaking, s.mode)
        assertEquals("job:1", s.sourceId)
        assertEquals("Nightly tests", s.title)
        assertEquals("storefront", s.subtitle)
        assertEquals(0, s.speakingBlock)
        assertEquals(
            listOf("Results", "All tests passed.", "Nothing to do."),
            f.engine.queue.map { it.text }
        )
        assertEquals(listOf(500L, 100L, 300L), f.engine.queue.map { it.pauseAfterMs })
        assertTrue(f.engine.queue.all { it.voiceId == "en-us-x-sfg-local" && it.rate == 1f })
        assertTrue(f.audio.active)
        assertEquals(NowPlayingInfo("Nightly tests", "storefront", true), f.nowPlaying.info)
    }

    @Test
    fun `the voice moves the highlight and progress, and the last segment ends it`() = runTest {
        val f = SpeechFixture(this)
        f.toggle(DOC)
        val total = "Results".length + "All tests passed.".length + "Nothing to do.".length

        f.engine.start(1)
        f.engine.range(1, 4, 9)
        assertEquals(1, f.store.state.value.speakingBlock)
        assertEquals(("Results".length + 9).toDouble() / total, f.store.state.value.progress, 1e-9)

        f.engine.done(1)
        assertEquals(Mode.Speaking, f.store.state.value.mode)
        f.engine.done(2)
        assertEquals(SpeechState(), f.store.state.value)
        assertFalse(f.audio.active)
        assertNull(f.nowPlaying.info)
    }

    @Test
    fun `the same message's button stops it, and code alone has nothing to read`() = runTest {
        val f = SpeechFixture(this)
        f.toggle(DOC)

        assertEquals(ReadAloudResult.Stopped, f.toggle(DOC))
        assertEquals(Mode.Idle, f.store.state.value.mode)
        assertEquals(ReadAloudResult.NothingToRead, f.toggle("```\nnpm test\n```"))
        assertEquals(Mode.Idle, f.store.state.value.mode)
    }

    @Test
    fun `another message replaces the one being read`() = runTest {
        val f = SpeechFixture(this)
        f.toggle(DOC)
        val first = f.engine.queue.first().id

        f.toggle("Second message.", sourceId = "job:2")

        assertEquals("job:2", f.store.state.value.sourceId)
        assertEquals(listOf("Second message."), f.engine.queue.map { it.text })
        f.store.onDone(first)
        assertEquals(Mode.Speaking, f.store.state.value.mode)
    }

    @Test
    fun `pausing stops the voice and resuming picks up at the word it was on`() = runTest {
        val f = SpeechFixture(this)
        f.toggle(DOC)
        f.engine.start(1)
        f.engine.range(1, 4, 9)
        val stale = f.engine.queue[1].id

        f.store.togglePause()
        assertEquals(Mode.Paused, f.store.state.value.mode)
        assertTrue(f.engine.queue.isEmpty())
        assertEquals(NowPlayingInfo("Nightly tests", "storefront", false), f.nowPlaying.info)
        f.store.onDone(stale)
        assertEquals(Mode.Paused, f.store.state.value.mode)

        f.store.togglePause()
        assertEquals(Mode.Speaking, f.store.state.value.mode)
        assertEquals(listOf("tests passed.", "Nothing to do."), f.engine.queue.map { it.text })
        f.engine.start(0)
        f.engine.range(0, 0, 5)
        val total = "Results".length + "All tests passed.".length + "Nothing to do.".length
        val spoken = "Results".length + 4 + 5
        assertEquals(spoken.toDouble() / total, f.store.state.value.progress, 1e-9)
    }

    @Test
    fun `skipping moves by sentence, and back after three seconds restarts one`() = runTest {
        val f = SpeechFixture(this)
        f.toggle(DOC)
        f.engine.start(0)

        f.store.skip(1)
        assertEquals("All tests passed.", f.engine.queue.first().text)
        f.store.skip(-1)
        assertEquals("Results", f.engine.queue.first().text)

        f.store.skip(1)
        f.engine.start(0)
        settle(3_500)
        f.store.skip(-1)
        assertEquals("All tests passed.", f.engine.queue.first().text)
    }

    @Test
    fun `elapsed counts while speaking and holds while paused`() = runTest {
        val f = SpeechFixture(this)
        f.toggle(DOC)

        settle(1_500)
        assertEquals(1.5, f.store.state.value.elapsed, 1e-9)
        f.store.togglePause()
        settle(2_000)
        assertEquals(1.5, f.store.state.value.elapsed, 1e-9)
        f.store.skip(1)
        settle(500)
        assertEquals(2.0, f.store.state.value.elapsed, 1e-9)
    }

    @Test
    fun `a new speed re-queues the voice from the current segment and is saved`() = runTest {
        val f = SpeechFixture(this)
        f.toggle(DOC)
        f.engine.start(1)

        f.store.setRate(1.5)

        assertEquals(1.5, f.prefs.speechPrefs.rate, 0.0)
        assertEquals(1.5, f.store.state.value.rate, 0.0)
        assertEquals(listOf("All tests passed.", "Nothing to do."), f.engine.queue.map { it.text })
        assertTrue(f.engine.queue.all { it.rate == 1.5f })
    }

    @Test
    fun `the Mac engine asks for the whole text and plays the clip it sends`() = runTest {
        val f = SpeechFixture(this)
        f.prefs.setSpeechEngine(SpeechPrefs.ENGINE_MAC)
        f.prefs.setSpeechRate(1.25)

        f.toggle(DOC)
        val s = f.store.state.value
        assertTrue(s.isLoading)
        assertEquals(Mode.Speaking, s.mode)
        assertTrue(f.engine.queue.isEmpty())
        assertFalse(f.audio.active)
        val (reqId, text) = f.requests.single()
        assertEquals("Results All tests passed. Nothing to do.", text)

        f.store.receiveSpeech("superseded", AUDIO, null)
        assertTrue(f.store.state.value.isLoading)
        f.store.receiveSpeech(reqId, AUDIO, null)

        val clip = f.clips.made.single()
        assertEquals("aac", clip.loaded?.decodeToString())
        assertEquals(1.25, clip.speed, 0.0)
        assertTrue(clip.playing)
        assertTrue(f.audio.active)
        assertFalse(f.store.state.value.isLoading)
        assertEquals(60.0, f.store.state.value.duration, 0.0)
        assertTrue(f.nowPlaying.info!!.seekable)
    }

    @Test
    fun `a clip skips by fifteen seconds, seeks, and maps its position to a block`() = runTest {
        val f = SpeechFixture(this)
        f.prefs.setSpeechEngine(SpeechPrefs.ENGINE_MAC)
        f.toggle(DOC)
        f.store.receiveSpeech(f.requests.single().first, AUDIO, null)
        val clip = f.clips.made.single()

        f.store.skip(1)
        assertEquals(15.0, f.store.state.value.elapsed, 0.0)
        assertEquals(1, f.store.state.value.speakingBlock)
        f.store.seek(0.0)
        assertEquals(0, f.store.state.value.speakingBlock)
        f.store.seek(59.0)
        assertEquals(1, f.store.state.value.speakingBlock)
        f.store.setRate(2.0)
        assertEquals(2.0, clip.speed, 0.0)
        assertTrue(f.engine.queue.isEmpty())

        clip.currentTime = 30.0
        settle(500)
        assertEquals(0.5, f.store.state.value.progress, 1e-9)

        clip.finish()
        assertEquals(Mode.Idle, f.store.state.value.mode)
        assertTrue(clip.stopped)
    }

    @Test
    fun `the Mac's failure is surfaced and outlives the stop`() = runTest {
        val f = SpeechFixture(this)
        f.prefs.setSpeechEngine(SpeechPrefs.ENGINE_MAC)
        f.toggle(DOC)

        f.store.receiveSpeech(f.requests.single().first, null, "No OpenAI key.")

        assertEquals(SpeechState(error = SpeechError.Mac("No OpenAI key.")), f.store.state.value)
        f.store.dismissError()
        assertNull(f.store.state.value.error)
    }

    @Test
    fun `unreadable or unplayable audio fails the reading`() = runTest {
        val f = SpeechFixture(this)
        f.prefs.setSpeechEngine(SpeechPrefs.ENGINE_MAC)
        f.toggle(DOC)
        f.store.receiveSpeech(f.requests.last().first, "not base64!", null)
        assertEquals(SpeechError.Synthesis, f.store.state.value.error)

        f.clips.playable = false
        f.toggle(DOC)
        f.store.receiveSpeech(f.requests.last().first, AUDIO, null)
        assertEquals(SpeechError.Playback, f.store.state.value.error)
        assertEquals(Mode.Idle, f.store.state.value.mode)
    }

    @Test
    fun `a speed change while the Mac renders doesn't start the phone's voice`() = runTest {
        val f = SpeechFixture(this)
        f.prefs.setSpeechEngine(SpeechPrefs.ENGINE_MAC)
        f.toggle(DOC)

        f.store.setRate(2.0)
        f.store.skip(1)

        assertTrue(f.engine.queue.isEmpty())
        f.store.receiveSpeech(f.requests.single().first, AUDIO, null)
        assertEquals(2.0, f.clips.made.single().speed, 0.0)
    }

    @Test
    fun `a sample plays outside the reader and hands the audio back when done`() = runTest {
        val f = SpeechFixture(this)
        f.prefs.setSpeechRate(0.75)

        f.store.preview("voice-a")

        val sample = f.engine.queue.single()
        assertEquals("This is how your automation replies will sound.", sample.text)
        assertEquals("voice-a", sample.voiceId)
        assertEquals(0.75f, sample.rate)
        assertFalse(f.store.state.value.isActive)
        assertNull(f.nowPlaying.info)
        assertTrue(f.audio.active)
        f.store.onDone(sample.id)
        assertFalse(f.audio.active)
    }

    @Test
    fun `interruptions and unplugged headphones pause, a short interruption resumes`() = runTest {
        val f = SpeechFixture(this)
        f.toggle(DOC)

        f.store.onInterruptionBegan()
        assertEquals(Mode.Paused, f.store.state.value.mode)
        f.store.onInterruptionEnded()
        assertEquals(Mode.Speaking, f.store.state.value.mode)
        f.store.onRouteLost()
        assertEquals(Mode.Paused, f.store.state.value.mode)
    }

    @Test
    fun `no engine on the phone fails the reading`() = runTest {
        val f = SpeechFixture(this)
        f.toggle(DOC)

        f.store.onUnavailable()

        assertEquals(SpeechState(error = SpeechError.Synthesis), f.store.state.value)
    }

    private companion object {
        const val DOC = "# Results\n\nAll tests passed. Nothing to do."
        val AUDIO: String = java.util.Base64.getEncoder().encodeToString("aac".toByteArray())
    }
}
