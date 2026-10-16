package cx.lpm.mobile.gallery

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import cx.lpm.mobile.core.designsystem.component.SegmentedControl
import cx.lpm.mobile.feature.files.fetch.FileFetch
import cx.lpm.mobile.feature.files.fetch.FilePreviewKind
import cx.lpm.mobile.feature.files.preview.DocumentPreview
import cx.lpm.mobile.feature.files.preview.FilePreviewSheet
import cx.lpm.mobile.feature.files.preview.FilePreviewUiState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The file preview's own viewers, which Demo Mode's text-only sample files never
 * reach: an image (pinch or double tap to zoom), a two-page PDF, a file the phone
 * can't show, and the copy's progress and failure states.
 */
@Composable
internal fun FilePreviewSample(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableIntStateOf(0) }
    val files by produceState<SampleFiles?>(null) {
        value = withContext(Dispatchers.IO) { SampleFiles.write(File(context.cacheDir, "gallery")) }
    }
    Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        SegmentedControl(
            options = listOf("Image", "PDF", "Other", "Loading", "Failed"),
            selectedIndex = page,
            onSelect = { page = it },
            modifier = Modifier.padding(16.dp)
        )
        val sample = files ?: return@Column
        val state = when (page) {
            0 -> ready(sample.image)

            1 -> ready(sample.pdf)

            2 -> ready(sample.other)

            3 -> FilePreviewUiState("assets/demo.mov", received = 3_400_000, total = 12_600_000)

            else -> FilePreviewUiState(
                "notes/missing.md",
                phase = FileFetch.Phase.Failed("No such file or directory")
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            FilePreviewSheet(state = state, onDone = onBack, onShare = {}, onRetry = {}) {
                DocumentPreview(it.file, "phone", {}, Dispatchers.IO)
            }
        }
    }
}

private fun ready(file: File) = FilePreviewUiState(
    path = "docs/${file.name}",
    phase = FileFetch.Phase.Ready(file, FilePreviewKind.Document, truncated = false)
)

private class SampleFiles(val image: File, val pdf: File, val other: File) {
    companion object {
        fun write(dir: File): SampleFiles {
            dir.mkdirs()
            val image = File(dir, "checkout.png")
            image.outputStream().use { banner().compress(Bitmap.CompressFormat.PNG, 100, it) }
            val pdf = File(dir, "spec.pdf")
            pdf.outputStream().use { out ->
                twoPages().apply {
                    writeTo(out)
                    close()
                }
            }
            val other = File(dir, "deck.key").apply {
                writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
            }
            return SampleFiles(image, pdf, other)
        }

        private fun banner(): Bitmap = createBitmap(1200, 800).also { bitmap ->
            Canvas(bitmap).apply {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.shader = LinearGradient(
                    0f,
                    0f,
                    1200f,
                    800f,
                    0xFF0A84FF.toInt(),
                    0xFF5E5CE6.toInt(),
                    Shader.TileMode.CLAMP
                )
                drawRect(0f, 0f, 1200f, 800f, paint)
                paint.shader = null
                paint.color = 0xFFFFFFFF.toInt()
                paint.textSize = 96f
                drawText("Checkout", 80f, 440f, paint)
            }
        }

        private fun twoPages(): PdfDocument = PdfDocument().apply {
            listOf("Checkout spec", "Payments").forEachIndexed { index, title ->
                val page = startPage(PdfDocument.PageInfo.Builder(612, 792, index + 1).create())
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 28f }
                page.canvas.drawText(title, 72f, 120f, paint)
                paint.textSize = 14f
                repeat(20) { line ->
                    page.canvas.drawText(
                        "Line ${line + 1} of page ${index + 1}.",
                        72f,
                        170f + line * 24f,
                        paint
                    )
                }
                finishPage(page)
            }
        }
    }
}
