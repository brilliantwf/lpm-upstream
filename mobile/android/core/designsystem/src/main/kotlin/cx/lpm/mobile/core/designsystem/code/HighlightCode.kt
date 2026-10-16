package cx.lpm.mobile.core.designsystem.code

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * A whole text in the syntax colors of its file extension [ext] (iOS
 * `DiffHighlighter.attributedDocument`), for a code editor or a file preview. Every
 * run is colored, plain ones in [SyntaxColors.plain], and the result's text is
 * exactly [text]: empty lines stay empty.
 */
fun highlightCode(text: String, ext: String, colors: SyntaxColors): AnnotatedString {
    val highlighter = SyntaxHighlighter(ext)
    return buildAnnotatedString {
        text.split("\n").forEachIndexed { index, line ->
            if (index > 0) withStyle(SpanStyle(color = colors.plain)) { append("\n") }
            highlighter.documentRuns(line).forEach { run ->
                withStyle(SpanStyle(color = colors.color(run.token))) { append(run.text) }
            }
        }
    }
}
