package cx.lpm.mobile.feature.files.fetch

import cx.lpm.mobile.core.data.client.LpmClient
import cx.lpm.mobile.core.data.client.fileChunk
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.data.session.SessionStore
import cx.lpm.mobile.core.model.FileChunk
import java.util.UUID
import kotlinx.coroutines.CoroutineScope

/**
 * The session's file fetches (iOS `FileFetchRouter`): each `fileChunk` reply goes
 * to the fetch that asked, by the token in its reqId; a dropped link re-asks for
 * what was in flight, and switching Macs cancels them all.
 */
class FileFetches internal constructor(
    private val session: MacSession,
    private val space: ScratchSpace,
    /** What copy calls this device ("phone"). */
    internal val noun: String,
    private val mimeType: (extension: String) -> String?,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) : SessionStore {
    private val fetches = LinkedHashMap<String, FileFetch>()

    internal val scope: CoroutineScope get() = session.scope

    internal val isReady: Boolean get() = session.state.value.isReady

    /** A fetch of the first of [candidates] that exists in [project]; [FileFetch.start] it. */
    fun fetch(project: String, candidates: List<String>): FileFetch =
        FileFetch(project, candidates, this)

    override fun wire(client: LpmClient) {
        client.onFileChunk = ::deliver
    }

    /** The phone switched Macs: a path means nothing on the new one. */
    override fun reset() {
        val open = fetches.values.toList()
        fetches.clear()
        open.forEach { it.cancel("Disconnected from the Mac this file is on.") }
    }

    override fun onConnectionReset() {
        fetches.values.toList().forEach(FileFetch::resend)
    }

    internal fun add(fetch: FileFetch) {
        fetches[fetch.token] = fetch
    }

    internal fun remove(token: String) {
        fetches -= token
    }

    internal fun newToken(): String = newId()

    internal fun send(reqId: String, project: String, path: String, offset: Long, length: Long) {
        session.client?.fileChunk(reqId, project, path, offset, length)
    }

    internal fun classify(name: String, head: ByteArray): FilePreviewKind =
        FilePreviewKind.classify(name, head, mimeType(pathExtension(name).lowercase()))

    internal fun scratch(token: String, name: String, onFailure: () -> Unit): ScratchFile =
        space.create(token, name, onFailure)

    private fun deliver(chunk: FileChunk) {
        val token = chunk.reqId.substringBefore(':')
        fetches[token]?.apply(chunk)
    }
}
