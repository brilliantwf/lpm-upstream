package cx.lpm.mobile.feature.notes.composer

import androidx.compose.ui.graphics.ImageBitmap

/** An image ready to attach: downscaled JPEG bytes plus the chip's thumbnail. */
class EncodedNoteImage(val data: ByteArray, val thumbnail: ImageBitmap?)

/** A picked file's bytes, or why there are none. */
sealed interface NoteFileRead {
    class Bytes(val data: ByteArray) : NoteFileRead

    data object TooLarge : NoteFileRead

    data object Unreadable : NoteFileRead
}

/**
 * The platform work behind note attachments — decoding, encoding and reading picks by
 * their content URI — kept apart so the composer runs anywhere. Every call does its
 * blocking work off the main thread.
 */
interface NoteMedia {
    suspend fun displayName(uri: String): String

    suspend fun mimeType(uri: String): String

    /** Downscale and JPEG-encode a picked or captured image; null if it can't be read. */
    suspend fun encodeImage(uri: String): EncodedNoteImage?

    /** A file's bytes, refusing anything over [limit] without reading it all. */
    suspend fun readFile(uri: String, limit: Int): NoteFileRead

    /** Drop a camera capture's file once its bytes are in memory. */
    suspend fun discardCapture(uri: String)
}
