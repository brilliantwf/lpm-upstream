package cx.lpm.mobile.core.demo

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope

/** Thursday 15 October 2026, 14:30:00.123 in New York. */
const val BASE_MS = 1_792_089_000_123L
val ZONE: ZoneId = ZoneId.of("America/New_York")

/** A clock that reads the test scheduler's virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class VirtualClock(
    private val scheduler: TestCoroutineScheduler,
    private val baseMs: Long = BASE_MS,
    private val zone: ZoneId = ZONE
) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = VirtualClock(scheduler, baseMs, zone)

    override fun instant(): Instant = Instant.ofEpochMilli(baseMs + scheduler.currentTime)
}

/** A frame the server pushed, at the virtual millisecond it was pushed. */
data class Pushed(val at: Long, val frame: Dict) {
    val t: String? get() = frame.string("t")
}

/** A [DemoServer] on virtual time, recording every frame it pushes. */
@OptIn(ExperimentalCoroutinesApi::class)
class DemoHarness(private val scope: TestScope) {
    val pushed = mutableListOf<Pushed>()
    val server = DemoServer(
        scope.backgroundScope,
        VirtualClock(scope.testScheduler)
    ).also { server ->
        server.deliver =
            { text -> pushed += Pushed(scope.testScheduler.currentTime, parseDict(text)!!) }
    }

    fun send(t: String, vararg fields: Pair<String, Any?>) {
        server.receive(encodeJson(mapOf("t" to t, *fields)))
    }

    /** Send a frame and return what it pushed right away. */
    fun request(t: String, vararg fields: Pair<String, Any?>): List<Pushed> {
        val before = pushed.size
        send(t, *fields)
        return pushed.drop(before)
    }

    /** Let [seconds] of virtual time pass, firing every timer due by then. */
    fun advance(seconds: Double) {
        scope.testScheduler.advanceTimeBy((seconds * 1000).roundToLong().milliseconds)
        scope.testScheduler.runCurrent()
    }

    /** Send a frame, let [seconds] pass, and return everything pushed meanwhile. */
    fun exchange(seconds: Double, t: String, vararg fields: Pair<String, Any?>): List<Pushed> {
        val before = pushed.size
        send(t, *fields)
        advance(seconds)
        return pushed.drop(before)
    }

    fun clear() = pushed.clear()
}

fun List<Pushed>.single(t: String): Dict = filter { it.t == t }.also {
    check(it.size == 1) { "expected one $t, got ${it.size}: ${map { p -> p.t }}" }
}.single().frame

@Suppress("UNCHECKED_CAST")
fun Dict.dictList(key: String): List<Dict> = this[key] as List<Dict>

fun Dict.sub(key: String): Dict = dict(key)!!
