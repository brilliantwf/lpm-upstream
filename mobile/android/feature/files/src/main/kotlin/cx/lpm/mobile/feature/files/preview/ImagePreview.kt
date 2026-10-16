package cx.lpm.mobile.feature.files.preview

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * An image fitted to the sheet, pinch or double tap to zoom (iOS Quick Look), or
 * [fallback] when the phone can't decode it.
 */
@Composable
internal fun ImagePreview(
    file: File,
    dispatcher: CoroutineDispatcher,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit
) {
    val image by produceState<Decoded>(Decoded.Loading, file) {
        value = withContext(dispatcher) { decode(file) }?.let(Decoded::Image) ?: Decoded.Failed
    }
    val zoom = rememberZoomState()
    Box(modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        when (val decoded = image) {
            Decoded.Loading -> ActivityIndicator()

            Decoded.Failed -> fallback()

            is Decoded.Image -> Image(
                bitmap = decoded.bitmap,
                contentDescription = file.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .zoomable(zoom, panWithOneFinger = true)
            )
        }
    }
}

private sealed interface Decoded {
    data object Loading : Decoded

    data object Failed : Decoded

    class Image(val bitmap: ImageBitmap) : Decoded
}

// About 8 megapixels: sharp at full zoom on a phone, 32 MB at most in memory.
private const val MAX_PIXELS = 8_000_000L

/** Decodes [file] at the largest power-of-two reduction that fits [MAX_PIXELS]. */
private fun decode(file: File): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth.toLong() / sample * (bounds.outHeight / sample) > MAX_PIXELS) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
}
