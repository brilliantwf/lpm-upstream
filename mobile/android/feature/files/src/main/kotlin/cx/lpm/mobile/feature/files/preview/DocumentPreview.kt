package cx.lpm.mobile.feature.files.preview

import android.webkit.MimeTypeMap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import cx.lpm.mobile.feature.files.fetch.pathExtension
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher

/** Images and PDFs in the phone's own viewers; anything else can only be shared on. */
@Composable
fun DocumentPreview(
    file: File,
    noun: String,
    onShare: (File) -> Unit,
    dispatcher: CoroutineDispatcher,
    modifier: Modifier = Modifier
) {
    val type = remember(file) {
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(pathExtension(file.name).lowercase())
    }
    val fallback: @Composable () -> Unit = { NoPreview(file, noun, onShare, modifier) }
    when {
        type == "application/pdf" -> PdfPreview(file, dispatcher, modifier, fallback)
        type?.startsWith("image/") == true -> ImagePreview(file, dispatcher, modifier, fallback)
        else -> fallback()
    }
}
