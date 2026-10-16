package cx.lpm.mobile.core.demo

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Replays `ios-parity/script.jsonl` (frames as the phone sends them, at virtual times)
 * and compares every pushed frame with `ios-parity/expected.jsonl`: the iOS DemoServer's
 * own replies to the same script, recorded on the same virtual clock and time zone by
 * `core/demo/ios-parity/generate.sh`. The only values normalized away are random ones
 * (session ids, upload ids). Regenerate both files after changing the iOS demo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IosParityTest {
    @Test
    fun repliesMatchIos() = runTest {
        val harness = DemoHarness(this)
        for (line in resourceLines("ios-parity/script.jsonl")) {
            val step = parseDict(line)!!
            val at = step.long("at")!!
            testScheduler.advanceTimeBy((at - testScheduler.currentTime).milliseconds)
            testScheduler.runCurrent()
            step.dict("frame")?.let { harness.server.receive(encodeJson(it)) }
            step.string("raw")?.let { harness.server.receive(it) }
        }

        val expected = resourceLines("ios-parity/expected.jsonl").map { parseDict(it)!! }
        val actual = harness.pushed.map { mapOf("at" to it.at, "frame" to it.frame) }
        val mismatches = (0 until maxOf(expected.size, actual.size)).mapNotNull { i ->
            val e = expected.getOrNull(i)?.let(::normalize)
            val a = actual.getOrNull(i)?.let(::normalize)
            if (e == a) null else "#$i\n  ios:     $e\n  android: $a"
        }
        assertEquals(
            "${mismatches.size} of ${expected.size} frames differ:\n" +
                mismatches.take(5).joinToString("\n"),
            0,
            mismatches.size
        )
    }

    private fun resourceLines(name: String): List<String> =
        javaClass.classLoader!!.getResource(name)!!.readText().lines().filter { it.isNotBlank() }

    private val sessionId = Regex("demo-sess-[0-9a-f]{8}")
    private val uuid = Regex("[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}")

    private fun normalize(value: Any?): Any? = when (value) {
        is Map<*, *> -> value.entries.associate { it.key to normalize(it.value) }
        is List<*> -> value.map(::normalize)
        is Number -> value.toDouble()
        is String -> value.replace(sessionId, "demo-sess-*").replace(uuid, "<uuid>")
        is JsonNull -> null
        else -> value
    }
}
