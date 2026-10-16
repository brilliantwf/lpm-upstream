package cx.lpm.mobile.feature.files.preview

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A PDF's pages one under another, pinch or double tap to zoom (iOS Quick Look), or
 * [fallback] for one the phone can't open (damaged, or locked with a password).
 */
@Composable
internal fun PdfPreview(
    file: File,
    dispatcher: CoroutineDispatcher,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit
) {
    val opened by produceState<PdfOpen>(PdfOpen.Loading, file) {
        val pages = withContext(dispatcher) { PdfPages.open(file, dispatcher) }
        value = pages?.let(PdfOpen::Pages) ?: PdfOpen.Failed
        if (pages != null) {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { pages.close() }
            }
        }
    }
    when (val open = opened) {
        PdfOpen.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ActivityIndicator()
        }

        PdfOpen.Failed -> fallback()

        is PdfOpen.Pages -> Pages(open.pages, modifier)
    }
}

@Composable
private fun Pages(pages: PdfPages, modifier: Modifier = Modifier) {
    val zoom = rememberZoomState()
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(LpmTheme.colors.secondarySystemBackground)
            .clipToBounds()
    ) {
        val width = constraints.maxWidth
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .zoomable(zoom, panWithOneFinger = false),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(pages.sizes.size) { index -> Page(pages, index, width) }
        }
    }
}

@Composable
private fun Page(pages: PdfPages, index: Int, width: Int) {
    val size = pages.sizes[index]
    val bitmap by produceState<ImageBitmap?>(null, pages, index, width) {
        value = pages.render(index, width * RENDER_SCALE)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(size.width.toFloat() / size.height.coerceAtLeast(1))
            .background(Color.White)
    ) {
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

// Rendered at twice the screen's width, so a zoomed page stays readable.
private const val RENDER_SCALE = 2

private sealed interface PdfOpen {
    data object Loading : PdfOpen

    data object Failed : PdfOpen

    class Pages(val pages: PdfPages) : PdfOpen
}

/** An open PDF; [PdfRenderer] takes one page at a time, on [dispatcher]. */
private class PdfPages(
    private val renderer: PdfRenderer,
    private val dispatcher: CoroutineDispatcher
) {
    private val lock = Mutex()
    private var closed = false

    val sizes: List<Size> = List(renderer.pageCount) { index ->
        renderer.openPage(index).use { Size(it.width, it.height) }
    }

    suspend fun render(index: Int, width: Int): ImageBitmap? = lock.withLock {
        withContext(dispatcher) {
            if (closed) return@withContext null
            renderer.openPage(index).use { page ->
                val height = (width.toLong() * page.height / page.width.coerceAtLeast(1)).toInt()
                val bitmap = createBitmap(width, height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.White.toArgb())
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap.asImageBitmap()
            }
        }
    }

    suspend fun close() {
        lock.withLock {
            closed = true
            withContext(dispatcher) { renderer.close() }
        }
    }

    companion object {
        fun open(file: File, dispatcher: CoroutineDispatcher): PdfPages? {
            val descriptor = try {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            } catch (_: IOException) {
                return null
            }
            return try {
                PdfPages(PdfRenderer(descriptor), dispatcher)
            } catch (_: IOException) {
                descriptor.close()
                null
            } catch (_: SecurityException) {
                descriptor.close()
                null
            }
        }
    }
}
