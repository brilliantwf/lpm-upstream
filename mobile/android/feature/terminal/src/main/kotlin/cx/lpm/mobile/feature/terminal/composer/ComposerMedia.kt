package cx.lpm.mobile.feature.terminal.composer

import androidx.compose.ui.graphics.ImageBitmap
import java.io.File

/** A photo, video or file the person picked, by its content URI. */
@JvmInline
value class PickedUri(val value: String)

/** An image ready to upload: base64 JPEG plus the chip's thumbnail. */
class EncodedImage(val b64: String, val thumbnail: ImageBitmap?)

/**
 * The platform work behind attachments — decoding, encoding and copying picked
 * media — kept apart so the composer's state machine runs anywhere. The
 * blocking calls run off the main thread.
 */
interface ComposerMedia {
    fun isVideo(source: PickedUri): Boolean

    fun displayName(source: PickedUri): String

    /** Downscale and JPEG-encode a picked or captured image; null if it can't be read. */
    fun encodeImage(source: PickedUri): EncodedImage?

    /**
     * Copy a pick into a scratch folder of its own, so a chunked upload can read it
     * piece by piece (and again on retry) after the picker's grant is gone.
     */
    fun copyToScratch(source: PickedUri): File?

    fun mime(file: File): String

    fun videoThumbnail(file: File): ImageBitmap?

    fun size(file: File): Long?

    fun readPiece(file: File, offset: Long, length: Long): ByteArray?

    fun readAll(file: File): ByteArray?

    fun discard(file: File)
}
