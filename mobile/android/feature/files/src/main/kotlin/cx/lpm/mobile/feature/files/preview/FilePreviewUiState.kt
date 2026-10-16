package cx.lpm.mobile.feature.files.preview

import androidx.compose.runtime.Immutable
import cx.lpm.mobile.feature.files.fetch.FileFetch
import cx.lpm.mobile.feature.files.fetch.FilePreviewKind
import cx.lpm.mobile.feature.files.fetch.lastPathComponent
import java.io.File

@Immutable
data class FilePreviewUiState(
    /** The file's path on the Mac (the reading being fetched). */
    val path: String,
    val phase: FileFetch.Phase = FileFetch.Phase.Loading,
    val received: Long = 0,
    val total: Long? = null,
    /** A text or Markdown file's contents once read off the copy; null until then. */
    val text: String? = null,
    /** Markdown shows its source rather than the rendered page (remembered for every file). */
    val markdownSource: Boolean = false
) {
    val filename: String get() = lastPathComponent(path)

    /** The copy to share: a whole file, never the head of a long text. */
    val shareable: File?
        get() = (phase as? FileFetch.Phase.Ready)?.takeUnless { it.truncated }?.file

    /** Markdown's Preview / Source switch sits on a bar that reaches up behind the title. */
    val barred: Boolean get() = (phase as? FileFetch.Phase.Ready)?.kind == FilePreviewKind.Markdown

    /** A bar fills in for a file of several ranges; a spinner turns for one. */
    val progress: Float?
        get() = total?.takeIf { it > FileFetch.CHUNK_SIZE }?.let { received.toFloat() / it }
}
