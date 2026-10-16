package cx.lpm.mobile.feature.notes.composer

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

/** A file queued for the next note: its bytes once they're prepared, held in memory only. */
@Immutable
class PendingNoteAttachment(
    val id: Long,
    val name: String,
    val mimeType: String,
    val data: ByteArray? = null,
    val thumbnail: ImageBitmap? = null
) {
    val ready: Boolean get() = data != null

    fun filled(data: ByteArray, thumbnail: ImageBitmap?) =
        PendingNoteAttachment(id, name, mimeType, data, thumbnail)
}
