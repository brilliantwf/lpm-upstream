package cx.lpm.mobile.feature.files.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.CapsuleButton
import cx.lpm.mobile.core.designsystem.component.EmptyState
import cx.lpm.mobile.core.designsystem.component.SegmentedControl
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.files.R
import cx.lpm.mobile.feature.files.fetch.FileFetch
import cx.lpm.mobile.feature.files.fetch.FilePreviewKind
import cx.lpm.mobile.feature.files.fetch.pathExtension
import java.io.File

/** What a loaded preview needs from its screen besides the file itself. */
class FilePreviewActions(
    val onMarkdownSource: (Boolean) -> Unit,
    /** Markdown text rendered in the web view. */
    val markdown: @Composable (String, Modifier) -> Unit,
    /** An image, a PDF or any other document. */
    val document: @Composable (File, Modifier) -> Unit,
    val media: @Composable (File, Modifier) -> Unit
)

/** The body of a loaded preview, picked by the file's kind (iOS `FilePreviewContent`). */
@Composable
fun FilePreviewContent(
    ready: FileFetch.Phase.Ready,
    state: FilePreviewUiState,
    line: Int,
    actions: FilePreviewActions,
    modifier: Modifier = Modifier
) {
    when (ready.kind) {
        FilePreviewKind.Text -> TextBody(ready, state, line, markdown = false, actions, modifier)

        FilePreviewKind.Markdown -> Column(modifier.fillMaxSize()) {
            MarkdownSwitch(state.markdownSource, actions.onMarkdownSource)
            TextBody(ready, state, line, markdown = true, actions, Modifier.weight(1f))
        }

        FilePreviewKind.Media -> actions.media(ready.file, modifier.fillMaxSize())

        FilePreviewKind.Document -> actions.document(ready.file, modifier.fillMaxSize())
    }
}

/** "Preview" / "Source": one choice for every Markdown file, remembered, like the desktop's. */
@Composable
private fun MarkdownSwitch(source: Boolean, onChange: (Boolean) -> Unit) {
    SegmentedControl(
        options = listOf(
            stringResource(R.string.files_view_preview),
            stringResource(R.string.files_view_source)
        ),
        selectedIndex = if (source) 1 else 0,
        onSelect = { onChange(it == 1) },
        modifier = Modifier
            .background(LpmTheme.colors.barMaterial)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun TextBody(
    ready: FileFetch.Phase.Ready,
    state: FilePreviewUiState,
    line: Int,
    markdown: Boolean,
    actions: FilePreviewActions,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val text = state.text
            when {
                text == null -> ActivityIndicator()

                text.isEmpty() -> EmptyState(
                    title = stringResource(R.string.files_empty),
                    icon = painterResource(DesignR.drawable.ic_description)
                )

                markdown && !state.markdownSource -> actions.markdown(text, Modifier.fillMaxSize())

                else -> CodeView(text, pathExtension(ready.file.name), line, Modifier.fillMaxSize())
            }
        }
        if (ready.truncated) {
            Text(
                text = stringResource(R.string.files_truncated),
                style = LpmTheme.typography.caption1,
                color = LpmTheme.colors.secondaryLabel,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LpmTheme.colors.barMaterial)
                    .padding(vertical = 8.dp)
            )
        }
    }
}

/** iOS can't show it, so neither can this phone: share it to open it in another app. */
@Composable
fun NoPreview(file: File, noun: String, onShare: (File) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            title = stringResource(R.string.files_no_preview),
            icon = painterResource(DesignR.drawable.ic_description),
            description = {
                Text(
                    text = stringResource(
                        R.string.files_no_preview_message,
                        noun.replaceFirstChar(Char::uppercase)
                    ),
                    style = LpmTheme.typography.body,
                    color = LpmTheme.colors.secondaryLabel,
                    textAlign = TextAlign.Center
                )
            },
            actions = {
                CapsuleButton(
                    text = stringResource(R.string.files_share),
                    onClick = { onShare(file) },
                    prominent = true,
                    textStyle = LpmTheme.typography.body
                )
            }
        )
    }
}
