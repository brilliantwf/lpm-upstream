package cx.lpm.mobile.feature.terminal.composer

import cx.lpm.mobile.core.model.UploadStep
import java.io.File
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sends one file to the Mac a terminal runs on, a piece at a time. Each piece waits
 * for the last one's acknowledgement because the Mac appends them in the order
 * they land, and only one piece is ever in memory, so a long video is bounded by
 * the Mac's disk rather than by one WebSocket frame.
 */
internal class ChunkedUpload(
    val file: File,
    private val termId: String,
    private val link: ComposerLink,
    private val media: ComposerMedia,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val onProgress: (Double) -> Unit,
    private val onFinish: (Outcome) -> Unit
) {
    sealed interface Outcome {
        data class Done(val path: String) : Outcome

        /**
         * `refusal` is the Mac's own reason, worth showing as is; without one the
         * link failed and a retry may well work.
         */
        data class Failed(val refusal: String?) : Outcome
    }

    private enum class Phase { Beginning, Sending, Finishing, Over }

    /** The step in flight; a reply for any other is stale. */
    var reqId = ""
        private set
    private var phase = Phase.Beginning
    private var uploadId = ""
    private var chunkSize = DEFAULT_CHUNK
    private var size = 0L
    private var offset = 0L
    private var watchdog: Job? = null

    fun start() {
        if (!link.isReady) return finish(Outcome.Failed(null))
        size = media.size(file) ?: return finish(Outcome.Failed("Couldn't read ${file.name}."))
        link.uploadBegin(termId, file.name, size, nextReqId())
        armTimeout(STEP_TIMEOUT_MS)
    }

    fun apply(step: UploadStep) {
        if (phase == Phase.Over || step.reqId != reqId) return
        watchdog?.cancel()
        if (!step.ok) return finish(Outcome.Failed(step.error ?: "Your Mac refused the file."))
        when (phase) {
            Phase.Beginning -> {
                uploadId = step.uploadId
                if (step.chunk > 0) chunkSize = step.chunk
                phase = Phase.Sending
                sendNext()
            }

            Phase.Sending -> {
                offset = step.received
                onProgress(offset.toDouble() / maxOf(size, 1).toDouble())
                sendNext()
            }

            Phase.Finishing -> finish(Outcome.Done(step.path))

            Phase.Over -> Unit
        }
    }

    /**
     * Stop without reporting — the chip is gone or the link dropped — and let the
     * Mac free whatever it has written so far.
     */
    fun cancel() {
        if (phase == Phase.Over) return
        phase = Phase.Over
        watchdog?.cancel()
        abortOnMac()
    }

    private fun sendNext() {
        if (offset >= size) {
            phase = Phase.Finishing
            link.uploadDone(termId, uploadId, nextReqId())
            armTimeout(DONE_TIMEOUT_MS)
            return
        }
        val from = offset
        val length = minOf(chunkSize, size - from)
        scope.launch {
            val b64 = withContext(io) {
                media.readPiece(file, from, length)?.let(Base64.getEncoder()::encodeToString)
            }
            if (phase != Phase.Sending || offset != from) return@launch
            if (b64 == null) return@launch finish(Outcome.Failed("Couldn't read ${file.name}."))
            link.uploadChunk(termId, uploadId, from, b64, nextReqId())
            armTimeout(STEP_TIMEOUT_MS)
        }
    }

    private fun finish(outcome: Outcome) {
        if (phase == Phase.Over) return
        if (outcome is Outcome.Failed) abortOnMac()
        phase = Phase.Over
        watchdog?.cancel()
        onFinish(outcome)
    }

    private fun abortOnMac() {
        if (uploadId.isNotEmpty()) link.uploadAbort(termId, uploadId)
    }

    private fun nextReqId(): String {
        reqId = UUID.randomUUID().toString()
        return reqId
    }

    /** A reply can be lost to a full queue on the Mac; without this the chip would spin forever. */
    private fun armTimeout(millis: Long) {
        watchdog?.cancel()
        watchdog = scope.launch {
            delay(millis)
            watchdog = null
            finish(Outcome.Failed(null))
        }
    }

    private companion object {
        const val DEFAULT_CHUNK = 1L shl 20
        const val STEP_TIMEOUT_MS = 60_000L

        // The Mac may still have to scp the file on to an ssh-backed pane.
        const val DONE_TIMEOUT_MS = 600_000L
    }
}
