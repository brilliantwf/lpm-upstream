package cx.lpm.mobile.core.data.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A one-shot timeout per key: each [arm] replaces the key's pending timeout, and
 * [cancel] / [cancelAll] drop it without firing. Runs on the scope's (main) thread.
 */
class GenerationTimeout<K>(private val scope: CoroutineScope) {
    private val jobs = mutableMapOf<K, Job>()

    fun arm(key: K, millis: Long, fire: () -> Unit) {
        jobs.remove(key)?.cancel()
        lateinit var job: Job
        job = scope.launch {
            delay(millis)
            if (jobs[key] === job) jobs.remove(key)
            fire()
        }
        jobs[key] = job
    }

    fun cancel(key: K) {
        jobs.remove(key)?.cancel()
    }

    fun cancelAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }
}
