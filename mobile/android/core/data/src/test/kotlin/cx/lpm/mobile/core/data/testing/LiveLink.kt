package cx.lpm.mobile.core.data.testing

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope

/** Moves virtual time on in heartbeat-sized steps with [socket] answering each one. */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.settleAlive(socket: FakeSocket, millis: Long) {
    var left = millis
    do {
        val step = minOf(left, HEARTBEAT_STEP_MS)
        testScheduler.advanceTimeBy(step)
        testScheduler.runCurrent()
        socket.answerPings()
        testScheduler.runCurrent()
        left -= step
    } while (left > 0)
}

private const val HEARTBEAT_STEP_MS = 5_000L
