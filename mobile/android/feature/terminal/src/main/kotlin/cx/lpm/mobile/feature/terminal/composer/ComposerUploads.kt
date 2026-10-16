package cx.lpm.mobile.feature.terminal.composer

import androidx.compose.ui.graphics.ImageBitmap
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val UPLOAD_FAILED = "Upload failed"
private const val UPLOAD_INTERRUPTED = "Upload interrupted"

/**
 * Photos and videos from the picker, in the order they were picked: each gets its
 * chip at once, and fills in once it's loaded, whatever order that finishes in.
 */
fun ComposerStore.addPicked(sources: List<PickedUri>) {
    for (source in sources) {
        if (media.isVideo(source)) {
            val id = reserveVideoPlaceholder()
            scope.launch {
                val file = withContext(io) { media.copyToScratch(source) }
                fillVideoUpload(id, file)
            }
        } else {
            addImage(source)
        }
    }
}

/** A camera, clipboard or library image: its chip now, encoded off the main thread. */
fun ComposerStore.addImage(source: PickedUri) {
    val id = reserveImagePlaceholder()
    scope.launch {
        val encoded = withContext(io) { media.encodeImage(source) }
        // Nothing to retry when it couldn't even be read: drop the chip.
        if (encoded == null) return@launch removeAttachment(id)
        setThumbnail(id, encoded.thumbnail)
        dispatchUpload(id, encoded.b64, "image/jpeg", null)
    }
}

internal fun ComposerStore.reserveImagePlaceholder(): String =
    appendAttachment(Attachment(Attachment.Kind.Image, "image.jpg", "image/jpeg"))

internal fun ComposerStore.appendAttachment(attachment: Attachment): String {
    update { s ->
        if (s.activeIndex !in s.tabs.indices) return@update s
        s.copy(
            tabs = s.tabs.replaced(s.activeIndex) {
                it.copy(
                    attachments =
                        it.attachments + attachment
                )
            }
        )
    }
    return attachment.id
}

/**
 * Tag the chip with a fresh reqId the server echoes (so the reply finds this exact
 * chip whatever order replies come in), keep the payload for a retry, note the
 * send order (for an old server) and send.
 */
internal fun ComposerStore.dispatchUpload(id: String, b64: String, mime: String, name: String?) {
    val reqId = UUID.randomUUID().toString()
    uploadPayloads[id] = ComposerStore.UploadPayload(b64, mime, name)
    pendingOrder += id
    updateAttachment(id) { it.copy(status = Attachment.Status.Uploading, reqId = reqId) }
    link.sendUpload(termId, b64, mime, name, reqId)
}

/** Retry with the kept payload and a new reqId, so a late reply for the old try can't land. */
fun ComposerStore.retryUpload(id: String) {
    fileSources[id]?.let { return uploadFile(id, it) }
    val payload = uploadPayloads[id] ?: return
    dispatchUpload(id, payload.b64, payload.mime, payload.name)
}

fun ComposerStore.removeAttachment(id: String) {
    update { s ->
        s.copy(
            tabs = s.tabs.map { tab ->
                tab.copy(
                    attachments = tab.attachments.filterNot {
                        it.id ==
                            id
                    }
                )
            }
        )
    }
    uploadPayloads.remove(id)
    pendingOrder.remove(id)
    pruneFileSources()
}

/**
 * An upload reply landed: find its chip by `reqId` (an empty one means an old
 * server, so the oldest chip still waiting). An empty path means it failed.
 */
fun ComposerStore.resolveUpload(reqId: String, path: String) {
    val attachmentId = if (reqId.isNotEmpty()) {
        current.tabs.flatMap {
            it.attachments
        }.firstOrNull { it.reqId == reqId && it.isPending }?.id
    } else {
        pendingOrder.firstOrNull()
    } ?: return
    pendingOrder.remove(attachmentId)
    updateAttachment(attachmentId) {
        it.copy(
            status = if (path.isEmpty()) {
                Attachment.Status.Failed(UPLOAD_FAILED)
            } else {
                Attachment.Status.Uploaded(path)
            }
        )
    }
    if (path.isNotEmpty()) uploadPayloads.remove(attachmentId)
}

/**
 * The link dropped: fail every chip still uploading so Send isn't blocked forever;
 * the kept payload lets the person retry once reconnected.
 */
fun ComposerStore.failInFlightUploads() {
    if (pendingOrder.isEmpty() && fileUploads.isEmpty()) return
    pendingOrder.clear()
    fileUploads.values.forEach { it.cancel() }
    fileUploads.clear()
    update { s ->
        s.copy(
            tabs = s.tabs.map { tab ->
                tab.copy(
                    attachments = tab.attachments.map {
                        if (it.isPending) {
                            it.copy(
                                status = Attachment.Status.Failed(UPLOAD_INTERRUPTED)
                            )
                        } else {
                            it
                        }
                    }
                )
            }
        )
    }
}

fun ComposerStore.dismissUploadError() = update { it.copy(uploadError = null) }

internal fun ComposerStore.updateAttachment(id: String, change: (Attachment) -> Attachment) =
    update { s ->
        val tabIndex = s.tabs.indexOfFirst { tab -> tab.attachments.any { it.id == id } }
        if (tabIndex < 0) return@update s
        s.copy(
            tabs = s.tabs.replaced(tabIndex) { tab ->
                tab.copy(attachments = tab.attachments.map { if (it.id == id) change(it) else it })
            }
        )
    }

internal fun ComposerStore.setThumbnail(id: String, thumbnail: ImageBitmap?) {
    if (thumbnail != null) updateAttachment(id) { it.copy(thumbnail = thumbnail) }
}

internal fun ComposerStore.holds(id: String): Boolean = current.tabs.any { tab ->
    tab.attachments.any {
        it.id ==
            id
    }
}
