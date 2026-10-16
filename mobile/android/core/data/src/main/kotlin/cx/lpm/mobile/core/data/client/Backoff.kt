package cx.lpm.mobile.core.data.client

import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * When the next connection attempt runs. Over a cellular Tailscale path the tunnel
 * flaps (direct ↔ DERP), so a connection can establish and drop within seconds;
 * retry with exponential backoff instead of dying on the first failure.
 */
internal class Backoff(private val scope: CoroutineScope, private val random: Random) {
    private var attempt = 0
    private var work: Job? = null

    /**
     * Arm the next attempt after a failure that looked like `hint`, and say what to
     * show meanwhile: hopeful ("connecting") for the first quick retry, then the
     * honest hint while the slow retries continue underneath. Null when one is
     * already armed.
     */
    fun schedule(hint: String, retry: () -> Unit): LpmClient.State? {
        if (work != null) return null
        attempt += 1
        val cap = if (hint == LpmClient.REFUSED_ERROR) REFUSED_MAX_S else MAX_S
        val capped = min(BASE_S * 2.0.pow(attempt - 1), cap)
        val delayMs = (capped * random.nextDouble(JITTER_LOW, JITTER_HIGH) * 1000).toLong()
        work = scope.launch {
            delay(delayMs)
            work = null
            retry()
        }
        return if (attempt <=
            PATIENT_ATTEMPTS
        ) {
            LpmClient.State.Connecting
        } else {
            LpmClient.State.Failed(hint)
        }
    }

    /** The link came up: the next failure starts over with a quick retry. */
    fun reset() {
        attempt = 0
    }

    fun cancel() {
        work?.cancel()
        work = null
        attempt = 0
    }

    private companion object {
        const val BASE_S = 1.5
        const val MAX_S = 20.0

        // A refusal means the Mac answered and only lpm isn't listening, so retry
        // fast enough to pick lpm up within seconds of it coming back.
        const val REFUSED_MAX_S = 4.0
        const val PATIENT_ATTEMPTS = 1
        const val JITTER_LOW = 0.85
        const val JITTER_HIGH = 1.15
    }
}
