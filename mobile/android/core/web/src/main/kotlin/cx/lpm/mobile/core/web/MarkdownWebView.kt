package cx.lpm.mobile.core.web

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.viewinterop.AndroidView

/**
 * A Markdown file rendered like the desktop's Files preview: GitHub-flavoured
 * Markdown with its inline HTML, sanitized, in a WebView (web/markdown/). Images
 * beside the file come from the Mac through [fileReader]; a link to another file
 * opens it ([onOpenFile]), and any other link opens outside the app.
 *
 * @param project the project the file belongs to.
 * @param path the Markdown file's own path on the Mac, which relative links
 *   resolve against.
 * @param darkTheme picks the page's dark stylesheet (`prefers-color-scheme`).
 * @param onOpenUrl an external link was tapped; opens in the browser by default.
 */
@Composable
fun MarkdownWebView(
    text: String,
    project: String,
    path: String,
    fileReader: MarkdownFileReader,
    onOpenFile: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isSystemInDarkTheme(),
    onOpenUrl: ((String) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val page = remember { MarkdownPage(scope) }
    val uriHandler = LocalUriHandler.current
    key(page.generation, darkTheme) {
        AndroidView(
            factory = { context -> page.create(context, darkTheme) },
            modifier = modifier,
            onRelease = page::release,
            update = {
                page.images.reader = fileReader
                page.images.project = project
                page.images.path = path
                page.onOpenFile = onOpenFile
                page.onOpenUrl = onOpenUrl ?: uriHandler::openInBrowser
                page.show(text)
            }
        )
    }
}
