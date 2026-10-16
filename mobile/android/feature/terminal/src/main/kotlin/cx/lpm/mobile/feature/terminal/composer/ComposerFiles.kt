package cx.lpm.mobile.feature.terminal.composer

import cx.lpm.mobile.core.model.UploadStep
import java.io.File
import java.util.Base64
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What an older Mac takes in one frame: it refuses frames over 16 MiB, and base64
 * inflates by 4/3.
 */
internal const val SINGLE_FRAME_LIMIT = 8L * 1024 * 1024

private const val MIB = 1024L * 1024

// Files and videos: copied to scratch first, then sent in pieces to a Mac that
// takes them, or in one frame to an older one, up to what a frame can hold.

fun ComposerStore.addFile(source: PickedUri) {
    val name = media.displayName(source)
    val id = appendAttachment(Attachment(Attachment.Kind.File, name, "application/octet-stream"))
    scope.launch {
        val copy = withContext(io) { media.copyToScratch(source) }
        if (copy == null) return@launch removeAttachment(id)
        updateAttachment(id) { it.copy(mime = media.mime(copy)) }
        adopt(id, copy)
    }
}

/** Hold a video's place in pick order while the library hands it over. */
internal fun ComposerStore.reserveVideoPlaceholder(): String =
    appendAttachment(Attachment(Attachment.Kind.Video, "Video", "video/quicktime"))

internal fun ComposerStore.fillVideoUpload(id: String, file: File?) {
    if (file == null) {
        removeAttachment(id)
        update { it.copy(uploadError = "Couldn't load that video from your library.") }
        return
    }
    updateAttachment(id) { it.copy(filename = file.name, mime = media.mime(file)) }
    // Before the upload: a finished one deletes the copy this reads.
    scope.launch {
        val thumbnail = withContext(io) { media.videoThumbnail(file) }
        setThumbnail(id, thumbnail)
        adopt(id, file)
    }
}

/** Send a scratch copy by whichever path this Mac speaks. */
internal fun ComposerStore.uploadFile(id: String, file: File) {
    updateAttachment(id) { it.copy(status = Attachment.Status.Uploading, progress = null) }
    if (link.takesFilesInPieces) sendInPieces(id, file) else sendInOneFrame(id, file)
}

fun ComposerStore.applyUploadStep(step: UploadStep) {
    fileUploads.values.firstOrNull { it.reqId == step.reqId }?.apply(step)
}

/** Drop the copies, and stop the uploads, of attachments no tab holds any more. */
internal fun ComposerStore.pruneFileSources() {
    val live = current.tabs.flatMap { tab -> tab.attachments.map { it.id } }.toSet()
    for (id in fileSources.keys.filterNot { it in live }) {
        fileUploads.remove(id)?.cancel()
        fileSources.remove(id)?.let(media::discard)
    }
}

/** Own a fresh scratch copy, unless its chip went while the copy was being made. */
private fun ComposerStore.adopt(id: String, file: File) {
    if (!holds(id)) return media.discard(file)
    fileSources[id] = file
    uploadFile(id, file)
}

private fun ComposerStore.sendInPieces(id: String, file: File) {
    val upload = ChunkedUpload(
        file = file,
        termId = termId,
        link = link,
        media = media,
        scope = scope,
        io = io,
        onProgress = { fraction -> updateAttachment(id) { it.copy(progress = fraction) } },
        onFinish = { outcome -> finishInPieces(id, outcome) }
    )
    fileUploads[id] = upload
    upload.start()
}

private fun ComposerStore.finishInPieces(id: String, outcome: ChunkedUpload.Outcome) {
    fileUploads.remove(id)
    when (outcome) {
        is ChunkedUpload.Outcome.Done -> {
            updateAttachment(id) { it.copy(status = Attachment.Status.Uploaded(outcome.path)) }
            fileSources.remove(id)?.let(media::discard)
        }

        is ChunkedUpload.Outcome.Failed -> {
            updateAttachment(id) { it.copy(status = Attachment.Status.Failed("Upload failed")) }
            outcome.refusal?.let { refusal ->
                update { it.copy(uploadError = refusal.replaceFirstChar(Char::uppercaseChar)) }
            }
        }
    }
}

private fun ComposerStore.sendInOneFrame(id: String, file: File) {
    val size = media.size(file) ?: 0
    if (size > SINGLE_FRAME_LIMIT) {
        removeAttachment(id)
        update {
            it.copy(
                uploadError = "${file.name} is ${size / MIB} MB. " +
                    "Update lpm on your Mac to attach files over 8 MB from your ${link.deviceNoun}."
            )
        }
        return
    }
    scope.launch {
        val b64 = withContext(io) { media.readAll(file)?.let(Base64.getEncoder()::encodeToString) }
        if (fileSources[id] != file) return@launch
        if (b64 == null) return@launch removeAttachment(id)
        // The one-frame path keeps its own payload for a retry.
        val mime = media.mime(file)
        fileSources.remove(id)
        media.discard(file)
        dispatchUpload(id, b64, mime, file.name)
    }
}
