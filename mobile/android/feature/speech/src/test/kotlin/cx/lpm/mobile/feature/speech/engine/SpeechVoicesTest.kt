package cx.lpm.mobile.feature.speech.engine

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechVoicesTest {
    private val us = Locale.US

    @Test
    fun `only downloaded offline voices in the phone's language, best first`() {
        val voices = listOf(
            voice("en-gb-x-gba-local", Locale.UK, 400),
            voice("en-us-x-iob-local", us, 300),
            voice("en-US-language", us, 400),
            voice("en-us-x-sfg-network", us, 500, needsNetwork = true),
            voice("en-us-x-tpd-local", us, 400, notInstalled = true),
            voice("de-de-x-deb-local", Locale.GERMANY, 500),
            voice("en-us-x-tpf-local", us, 400),
            voice("en-us-x-sfg-local", us, 400),
            voice("en-au-x-aua-local", Locale.forLanguageTag("en-AU"), 500)
        )

        val offered = SpeechVoices.installed(voices, us, engineDefault = "en-us-x-tpf-local")

        assertEquals(
            listOf(
                "en-au-x-aua-local",
                "en-us-x-tpf-local",
                "en-us-x-sfg-local",
                "en-gb-x-gba-local",
                "en-us-x-iob-local"
            ),
            offered.map { it.id }
        )
        assertEquals(
            listOf(
                SpeechVoice.Quality.Premium,
                SpeechVoice.Quality.Enhanced,
                SpeechVoice.Quality.Enhanced,
                SpeechVoice.Quality.Enhanced,
                SpeechVoice.Quality.Default
            ),
            offered.map { it.quality }
        )
        assertEquals(listOf(false, true, false, false, false), offered.map { it.isEngineDefault })
    }

    @Test
    fun `machine names read as the language and the voice's code`() {
        assertEquals(
            "English (United States) · SFG",
            SpeechVoices.label(voice("en-us-x-sfg-local", us, 400), us)
        )
        assertEquals("Samantha", SpeechVoices.label(voice("Samantha", us, 300), us))
    }

    private fun voice(
        name: String,
        locale: Locale,
        quality: Int,
        needsNetwork: Boolean = false,
        notInstalled: Boolean = false
    ) = EngineVoice(name, locale, quality, needsNetwork, notInstalled)
}
