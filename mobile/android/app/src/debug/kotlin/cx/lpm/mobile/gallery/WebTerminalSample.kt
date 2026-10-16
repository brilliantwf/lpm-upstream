package cx.lpm.mobile.gallery

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import cx.lpm.mobile.core.designsystem.R
import cx.lpm.mobile.core.designsystem.component.GlassButtonGroup
import cx.lpm.mobile.core.designsystem.component.GlassGroupButton
import cx.lpm.mobile.core.designsystem.component.LpmTopBar
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.web.TerminalPrefs
import cx.lpm.mobile.core.web.TerminalWebController
import cx.lpm.mobile.core.web.TerminalWebView
import cx.lpm.mobile.core.web.rememberTerminalWebController

/**
 * xterm.js in the WebView, seeded with the demo's Terminal 1 and echoing what is
 * typed like a shell (`ls`, `open`, `clear`). The bar's buttons raise and dismiss
 * the keyboard through the controller.
 */
@Composable
internal fun WebTerminalSample(onBack: () -> Unit, modifier: Modifier = Modifier) {
    LpmTheme(darkTheme = true) {
        val context = LocalContext.current
        val controller = rememberTerminalWebController()
        val shell = remember(controller) { EchoShell(controller) }
        val theme = TerminalPrefs.DefaultTheme
        LaunchedEffect(controller) {
            controller.seed(EchoShell.SEED, reset = true, ptyCols = 0, ptyRows = 0)
        }
        Column(
            modifier
                .fillMaxSize()
                .background(theme.backgroundColor)
                .navigationBarsPadding()
                .imePadding()
        ) {
            LpmTopBar(
                title = "Terminal 1",
                leading = { BackButton(onBack) },
                trailing = {
                    GlassButtonGroup {
                        GlassGroupButton(
                            painterResource(R.drawable.ic_edit),
                            "Type",
                            controller::focus
                        )
                        GlassGroupButton(
                            painterResource(R.drawable.ic_keyboard_arrow_down),
                            "Dismiss keyboard",
                            controller::hideKeyboard
                        )
                    }
                }
            )
            TerminalWebView(
                controller = controller,
                onInput = shell::input,
                onResize = { cols, rows -> Log.d("WebTerminalSample", "resize $cols×$rows") },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                theme = theme,
                onFirstContent = { Log.d("WebTerminalSample", "first content") },
                onOpenPath = { paths, line ->
                    Toast.makeText(
                        context,
                        "Open ${paths.first()}:$line",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onOpenUrl = { Toast.makeText(context, "Open $it", Toast.LENGTH_SHORT).show() }
            )
        }
    }
}

/** A stand-in for the Mac's shell: echoes keystrokes and answers a few commands. */
private class EchoShell(private val controller: TerminalWebController) {
    private val line = StringBuilder()

    fun input(data: String) {
        if (data.startsWith(ESC)) return
        for (c in data) {
            when {
                c == '\r' -> {
                    controller.feed("\r\n" + run(line.toString().trim()))
                    line.clear()
                }

                c == DEL -> if (line.isNotEmpty()) {
                    line.deleteAt(line.lastIndex)
                    controller.feed("\b \b")
                }

                c >= ' ' -> {
                    line.append(c)
                    controller.feed(c.toString())
                }
            }
        }
    }

    private fun run(command: String): String = when (command) {
        "" -> PROMPT
        "ls" -> LS + PROMPT
        "clear" -> "$ESC[2J$ESC[3J$ESC[H$PROMPT"
        "open" -> "See app/checkout/page.tsx:42 and https://lpm.cx/docs\r\n$PROMPT"
        else -> "zsh: command not found: $command\r\n$PROMPT"
    }

    companion object {
        private const val ESC = '\u001B'
        private const val DEL = '\u007F'
        private const val RESET = "$ESC[0m"
        private const val DIR = "$ESC[1;34m"
        private const val PROMPT = "$ESC[1;36m~/dev/storefront$RESET $ESC[2m$$RESET "
        private const val LS =
            "${DIR}app$RESET  ${DIR}components$RESET  ${DIR}lib$RESET  ${DIR}public$RESET  " +
                "next.config.mjs  package.json  README.md  tsconfig.json\r\n"
        const val SEED = PROMPT + "ls\r\n" + LS + PROMPT
    }
}
