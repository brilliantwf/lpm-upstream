package cx.lpm.mobile.feature.speech.ui

import cx.lpm.mobile.core.data.prefs.SpeechPrefs
import org.junit.Assert.assertEquals
import org.junit.Test

class RateLabelTest {
    @Test
    fun `whole speeds drop the decimals`() {
        assertEquals(
            listOf("0.75x", "1x", "1.25x", "1.5x", "2x"),
            SpeechPrefs.RATES.map(::rateLabel)
        )
    }
}
