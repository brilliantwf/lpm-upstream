package cx.lpm.mobile.feature.notes.data

import cx.lpm.mobile.core.data.client.requestNotesAttachment
import cx.lpm.mobile.core.data.session.GenerationTimeout
import cx.lpm.mobile.core.data.session.MacSession
import java.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A file the user is about to attach to a note. Raw bytes, held in memory only. */
class NoteAttachmentDraft(val name: String, val mimeType: String, val data: ByteArray) {
    /** As `notesAddMessage` carries it inline. */
    fun wire(): JsonObject = buildJsonObject {
        put("name", name)
        put("mimeType", mimeType)
        put("data", Base64.getEncoder().encodeToString(data))
    }
}

data class NoteAttachmentsState(
    val loading: Set<String> = emptySet(),
    val error: Map<String, String> = emptyMap(),
    /** Hashes whose bytes are in memory now. */
    val cached: Set<String> = emptySet()
)

/**
 * Attachment bytes for the notes screens, cached by hash for the session. They live
 * here and nowhere else — never on the phone's disk — because the notebook is
 * encrypted on the Mac and this link is the only place its plaintext exists off it.
 */
class NoteAttachments internal constructor(private val session: MacSession) {
    private val mutable = MutableStateFlow(NoteAttachmentsState())
    val state: StateFlow<NoteAttachmentsState> = mutable.asStateFlow()

    private val cache = LinkedHashMap<String, ByteArray>()
    private val timeout = GenerationTimeout<String>(session.scope)

    fun data(hash: String): ByteArray? = cache[hash]

    /**
     * Fetch one attachment's bytes, at most once per session. [size] comes from the
     * note itself, so an over-cap file is refused without asking.
     */
    fun fetch(project: String, hash: String, size: Int) {
        if (hash in cache || hash in state.value.loading) return
        if (size > BYTE_LIMIT) {
            mutable.update {
                it.copy(
                    error = it.error +
                        (
                            hash to "This attachment is too large to open on your phone. " +
                                "Open it on your Mac instead."
                            )
                )
            }
            return
        }
        mutable.update { it.copy(loading = it.loading + hash, error = it.error - hash) }
        session.client?.requestNotesAttachment(project, hash)
        timeout.arm(hash, TIMEOUT_MS) {
            if (hash !in state.value.loading) return@arm
            mutable.update {
                it.copy(
                    loading = it.loading - hash,
                    error = it.error + (hash to "Couldn't open the attachment. Try again.")
                )
            }
        }
    }

    internal fun apply(hash: String, data: String?, error: String?) {
        timeout.cancel(hash)
        val bytes = data?.let(::decode)
        if (bytes == null) {
            mutable.update {
                it.copy(
                    loading = it.loading - hash,
                    error = it.error + (hash to (error ?: "Couldn't open the attachment."))
                )
            }
            return
        }
        store(hash, bytes)
        mutable.update {
            it.copy(
                loading = it.loading - hash,
                error = it.error - hash,
                cached = cache.keys.toSet()
            )
        }
    }

    internal fun handleConnectionReset() {
        timeout.cancelAll()
        mutable.update { it.copy(loading = emptySet()) }
    }

    internal fun reset() {
        timeout.cancelAll()
        cache.clear()
        mutable.value = NoteAttachmentsState()
    }

    /** Oldest first out once the cache passes its ceiling, always keeping the newest. */
    private fun store(hash: String, bytes: ByteArray) {
        cache.remove(hash)
        cache[hash] = bytes
        var total = cache.values.sumOf { it.size.toLong() }
        val order = cache.keys.iterator()
        while (total > CACHE_LIMIT && cache.size > 1) {
            val oldest = order.next()
            total -= cache[oldest]?.size ?: 0
            order.remove()
        }
    }

    private fun decode(encoded: String): ByteArray? = try {
        Base64.getDecoder().decode(encoded)
    } catch (_: IllegalArgumentException) {
        null
    }

    companion object {
        /**
         * Per-file ceiling on this link — attachments ride inline as base64, which the
         * Mac refuses above this. Bigger files stay a Mac job.
         */
        const val BYTE_LIMIT = 8 * 1024 * 1024
        private const val CACHE_LIMIT = 32L * 1024 * 1024
        private const val TIMEOUT_MS = 60_000L
    }
}
