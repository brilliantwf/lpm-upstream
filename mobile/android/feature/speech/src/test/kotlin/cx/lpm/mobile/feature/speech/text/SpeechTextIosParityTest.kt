package cx.lpm.mobile.feature.speech.text

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Every document in `ios-parity/cases.json`, read by the port, against what the iOS
 * `SpeechText` speaks for it (`ios-parity/expected.jsonl`, from `ios-parity/generate.sh`).
 * Robolectric supplies the phone's ICU sentence breaker.
 */
@RunWith(RobolectricTestRunner::class)
class SpeechTextIosParityTest {
    @Test
    fun `every document reads the way iOS reads it`() {
        val readings = resource("ios-parity/expected.jsonl").lines().filter { it.isNotBlank() }
        val mismatches = readings.map(::JSONObject).mapNotNull { reading ->
            val markdown = reading.getString("markdown")
            val expected = reading.getJSONArray("segments").let { array ->
                (0 until array.length()).map { i ->
                    val s = array.getJSONObject(i)
                    Triple(s.getInt("block"), s.getString("text"), s.getLong("pauseMs"))
                }
            }
            val actual = SpeechText.segments(markdown).map {
                Triple(it.blockId, it.text, it.pauseMs)
            }
            if (actual == expected) null else "$markdown\n  iOS:     $expected\n  Android: $actual"
        }
        assertEquals(mismatches.joinToString("\n\n"), 0, mismatches.size)
        assertEquals(54, readings.size)
    }

    @Test
    fun `segment ids count the spoken segments`() {
        val segments = SpeechText.segments("# Title\n\n```\ncode\n```\n\nOne. Two.")
        assertEquals(listOf(0, 1, 2), segments.map { it.id })
        assertEquals(listOf(0, 2, 2), segments.map { it.blockId })
    }

    private fun resource(path: String): String =
        checkNotNull(javaClass.classLoader?.getResource(path)).readText()
}
