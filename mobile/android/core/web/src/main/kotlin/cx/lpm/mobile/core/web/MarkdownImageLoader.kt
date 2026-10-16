package cx.lpm.mobile.core.web

import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking

/** Reads a file of a project from the Mac, the way the Files preview fetches it. */
fun interface MarkdownFileReader {
    /** The first of [candidates] that exists in [project], as a local copy; null if none. */
    suspend fun read(project: String, candidates: List<String>): File?
}

/**
 * Serves the images a Markdown file names by path (`lpm-file://image?src=…`,
 * rewritten by markdown.js) by fetching them from the Mac, the way the desktop
 * preview reads them from disk beside the file. The WebView asks from its own
 * request threads; the fetch runs in [scope] (the screen's, on the main thread).
 */
internal class MarkdownImageLoader(scope: CoroutineScope) {
    // One failed read must not cancel the others.
    private val scope = scope + SupervisorJob(scope.coroutineContext[Job])

    @Volatile var reader: MarkdownFileReader? = null

    @Volatile var project = ""

    /** The Markdown file's own path on the Mac, which relative sources resolve against. */
    @Volatile var path = ""
    private val fetches: MutableSet<Deferred<File?>> = Collections.synchronizedSet(HashSet())

    fun intercept(url: Uri): WebResourceResponse? {
        if (url.scheme != SCHEME) return null
        val reader = reader
        val link = url.getQueryParameter("src")?.let { MarkdownLink.resolve(it, path) }
        if (reader == null || link !is MarkdownLink.File) return missing()
        val project = project
        val fetch = scope.async(start = CoroutineStart.LAZY) { reader.read(project, link.paths) }
        fetches += fetch
        // A cancelled fetch (the view went away) or a failed one both read as missing.
        val file = try {
            runBlocking { fetch.await() }
        } catch (_: Exception) {
            null
        } finally {
            fetches -= fetch
        }
        return file?.takeIf { it.isFile }?.let(::response) ?: missing()
    }

    fun cancelAll() {
        synchronized(fetches) {
            fetches.forEach { it.cancel() }
            fetches.clear()
        }
    }

    private fun response(file: File): WebResourceResponse {
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
        return WebResourceResponse(
            mime ?: "application/octet-stream",
            null,
            200,
            "OK",
            emptyMap(),
            FileInputStream(file)
        )
    }

    /** A file the Mac can't send fails the request, so the page shows its alt text. */
    private fun missing() = WebResourceResponse(
        "text/plain",
        "utf-8",
        404,
        "Not Found",
        emptyMap(),
        ByteArrayInputStream(ByteArray(0))
    )

    companion object {
        const val SCHEME = "lpm-file"
    }
}
