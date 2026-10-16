package cx.lpm.mobile.feature.usage

import cx.lpm.mobile.core.data.claude.ClaudeAccountsStore
import cx.lpm.mobile.core.data.session.SessionStore
import cx.lpm.mobile.core.data.usage.StatsStore
import cx.lpm.mobile.core.data.usage.UsageStore
import cx.lpm.mobile.core.testing.FakeSocket
import cx.lpm.mobile.core.testing.TestSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope

/** The stores the usage screens read, beside Projects and Terminals. */
val UsageStores: Set<SessionStore.Factory> = setOf(
    SessionStore.Factory(::UsageStore),
    SessionStore.Factory(::StatsStore),
    SessionStore.Factory(::ClaudeAccountsStore)
)

/** A session in Demo Mode with the sample Mac's first answers in. */
fun TestScope.demoSession(): TestSession = TestSession(this, UsageStores).apply {
    enterDemo()
    settle()
}

/** Let the demo Mac answer everything sent so far. */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestSession.settle(millis: Long = SETTLE_MS) {
    scheduler.advanceTimeBy(millis)
    scheduler.runCurrent()
}

/**
 * A connected Mac that answers every heartbeat, so the link outlasts the 30 seconds a
 * silent one survives while virtual time moves on.
 */
class LiveMac(private val session: TestSession, val socket: FakeSocket) {
    private var pongs = 0

    @OptIn(ExperimentalCoroutinesApi::class)
    fun settle(millis: Long = 0) {
        var left = millis
        do {
            val step = minOf(left, HEARTBEAT_STEP_MS)
            session.scheduler.advanceTimeBy(step)
            session.scheduler.runCurrent()
            repeat(socket.sentOf("ping").size - pongs) {
                pongs++
                socket.receive("""{"t":"pong"}""")
            }
            session.scheduler.runCurrent()
            left -= step
        } while (left > 0)
    }
}

/** Connect to a saved Mac that stays answering. */
fun TestSession.connectLive(): LiveMac = LiveMac(this, connectReady())

private const val SETTLE_MS = 1_000L
private const val HEARTBEAT_STEP_MS = 5_000L
