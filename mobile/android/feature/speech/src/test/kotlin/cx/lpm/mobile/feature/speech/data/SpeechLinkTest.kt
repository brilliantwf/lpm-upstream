package cx.lpm.mobile.feature.speech.data

import cx.lpm.mobile.core.data.prefs.SpeechPrefs
import cx.lpm.mobile.core.data.prefs.setSpeechEngine
import cx.lpm.mobile.core.data.session.SessionStore
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.testing.TestSession
import cx.lpm.mobile.core.testing.settle
import cx.lpm.mobile.feature.speech.SpeechFixture
import cx.lpm.mobile.feature.speech.ui.SpeechViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpeechLinkTest {
    @Test
    fun `the Mac engine asks the reading session's Mac and gets its answer`() = runTest {
        lateinit var fixture: SpeechFixture
        val t = TestSession(this, setOf(SessionStore.Factory { SpeechLink(it) { fixture.store } }))
        fixture = SpeechFixture(this, t.prefs)
        val vm = SpeechViewModel(fixture.store, t.sessions)
        t.prefs.setSpeechEngine(SpeechPrefs.ENGINE_MAC)
        t.enterDemo()

        vm.toggle("job:1", "Nightly tests", "storefront", "All tests passed.", SessionKey.Live)
        settle(1_000)

        assertEquals(
            SpeechError.Mac(
                "Reading aloud on the Mac isn't available in the demo. " +
                    "Switch the engine to This iPhone."
            ),
            fixture.store.state.value.error
        )
    }
}
