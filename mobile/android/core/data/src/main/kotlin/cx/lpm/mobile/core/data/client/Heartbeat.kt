package cx.lpm.mobile.core.data.client

import cx.lpm.mobile.core.protocol.Wire
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps a live socket warm and notices a silent drop: a ping every 20 s whose pong
 * must come within 10 s, plus the bounded foreground [probe]. OkHttp has no ping
 * with a completion, so this is the protocol's own `ping`/`pong` round trip, which
 * every lpm desktop answers.
 */
internal class Heartbeat(
    private val scope: CoroutineScope,
    private val clock: Clock,
    private val current: () -> Socket?,
    private val fail: (Throwable?) -> Unit,
    private val alive: () -> Unit
) {
    private var ticker: Job? = null
    private var deadline: Job? = null
    private var armedAt: Instant? = null
    private var probeDeadline: Job? = null
    private val waiters = mutableListOf<Pair<Socket, (Throwable?) -> Unit>>()

    val isProbing: Boolean get() = probeDeadline != null

    fun start() {
        stopTicking()
        ticker = scope.launch {
            while (true) {
                delay(INTERVAL_MS)
                tick()
            }
        }
    }

    /** The socket is going away: drop everything waiting on it. */
    fun stop() {
        stopTicking()
        probeDeadline?.cancel()
        probeDeadline = null
        waiters.clear()
    }

    /**
     * A `Ready` link after the app was backgrounded is often stale (a half-open
     * path even accepts sends), so ping now with a short deadline of its own — on a
     * dead path the ping can hang far longer than the user will wait.
     */
    fun probe() {
        val s = current() ?: return
        if (probeDeadline != null) return
        probeDeadline = after(PROBE_TIMEOUT_MS) {
            probeDeadline = null
            if (s === current()) fail(null)
        }
        ping(s) { error ->
            probeDeadline?.cancel()
            probeDeadline = null
            if (s === current() && error != null) fail(error)
        }
    }

    fun pong(s: Socket) {
        val answered = waiters.filter { it.first === s }
        waiters.removeAll(answered)
        answered.forEach { it.second(null) }
    }

    private fun tick() {
        val s = current() ?: return
        // A ping still out from the previous tick is already a dead link — unless the
        // app was suspended through the wait, in which case the probe decides.
        if (deadline != null) {
            if (wasSuspendedSinceArming()) {
                stopWaiting()
                probe()
            } else {
                fail(null)
            }
            return
        }
        // On a black-holed path (wedged tunnel, Wi-Fi→cellular hop with no RST) the
        // pong never comes, so the deadline is what detects the drop.
        deadline = after(PONG_TIMEOUT_MS) {
            val suspended = wasSuspendedSinceArming()
            deadline = null
            armedAt = null
            if (s !== current()) return@after
            if (suspended) probe() else fail(null)
        }
        armedAt = clock.instant()
        ping(s) { error ->
            if (s !== current()) return@ping
            stopWaiting()
            if (error != null) fail(error) else alive()
        }
    }

    // Work doesn't run while the app is suspended, so a deadline can fire long after
    // it was due with nothing wrong with the link. Overshoot of 3x is read as "we
    // were suspended" — a really dead link overshoots by milliseconds.
    private fun wasSuspendedSinceArming(): Boolean {
        val armed = armedAt ?: return false
        return Duration.between(armed, clock.instant()).toMillis() > PONG_TIMEOUT_MS * 3
    }

    private fun ping(s: Socket, done: (Throwable?) -> Unit) {
        waiters += s to done
        if (!s.send(Wire.ping())) {
            scope.launch {
                if (waiters.remove(s to done)) done(IOException("ping not sent"))
            }
        }
    }

    private fun stopWaiting() {
        deadline?.cancel()
        deadline = null
        armedAt = null
    }

    private fun stopTicking() {
        ticker?.cancel()
        ticker = null
        stopWaiting()
    }

    private fun after(millis: Long, block: () -> Unit): Job = scope.launch {
        delay(millis)
        block()
    }

    private companion object {
        const val INTERVAL_MS = 20_000L

        // Lenient (Tailscale over cellular can have multi-second RTT), but far
        // shorter than the minutes a black-holed path leaves a ping unanswered.
        const val PONG_TIMEOUT_MS = 10_000L
        const val PROBE_TIMEOUT_MS = 4_000L
    }
}
