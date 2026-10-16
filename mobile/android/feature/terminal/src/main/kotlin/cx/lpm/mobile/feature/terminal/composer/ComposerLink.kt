package cx.lpm.mobile.feature.terminal.composer

/**
 * What a [ComposerStore] needs from the session: the requests it sends and what it
 * reads about the link (iOS `ComposerStore.model`).
 */
interface ComposerLink {
    /** This phone's paired device id, to drop the echo of a draft it sent. */
    val selfDeviceId: String?

    val isReady: Boolean

    /** The Mac takes files in pieces (`features` holds "fileUpload"). */
    val takesFilesInPieces: Boolean

    /** What copy calls this device ("phone"). */
    val deviceNoun: String

    /** Mirror a draft to the Mac, which fans it out to the desktop and other phones. */
    fun sendDraft(termId: String, text: String)

    /** One attachment in one frame, tagged with the `reqId` the server echoes. */
    fun sendUpload(termId: String, b64: String, mime: String, name: String?, reqId: String)

    /** Start an AI rewrite; returns the reqId its replies carry. */
    fun runTransform(
        termId: String,
        project: String,
        instruction: String,
        text: String,
        variants: Int
    ): String

    /** Forget a rewrite's route (cancel, timeout), so it can't leak. */
    fun clearTransformRoute(reqId: String?)

    fun uploadBegin(termId: String, name: String, bytes: Long, reqId: String)

    fun uploadChunk(termId: String, uploadId: String, offset: Long, b64: String, reqId: String)

    fun uploadDone(termId: String, uploadId: String, reqId: String)

    fun uploadAbort(termId: String, uploadId: String)
}
