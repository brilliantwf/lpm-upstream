package cx.lpm.mobile.feature.notes.attachment

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [bytes] decoded off the main thread, no larger than [maxDimension] pixels; null until then. */
@Composable
internal fun rememberNoteImage(bytes: ByteArray?, maxDimension: Int): State<ImageBitmap?> =
    produceState<ImageBitmap?>(null, bytes, maxDimension) {
        value =
            bytes?.let { withContext(Dispatchers.Default) { decodeNoteImage(it, maxDimension) } }
    }

internal fun decodeNoteImage(bytes: ByteArray, maxDimension: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}
