package cx.lpm.mobile.feature.git.ask

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.EmptyState
import cx.lpm.mobile.core.designsystem.component.GlassCapsuleButton
import cx.lpm.mobile.core.designsystem.component.ListDefaults
import cx.lpm.mobile.core.designsystem.component.ListHeaderStyle
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.component.LpmTopBar
import cx.lpm.mobile.core.designsystem.component.ModalSheet
import cx.lpm.mobile.core.designsystem.component.TextFieldRow
import cx.lpm.mobile.core.designsystem.component.rememberModalSheetState
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.TerminalInfo
import cx.lpm.mobile.feature.git.R
import cx.lpm.mobile.feature.git.review.AskContext

/**
 * "Ask agent…" (iOS `AgentAskSheet`): an instruction, which of the project's live
 * terminals to ask, and a preview of the diff that goes with it. [onSend] gets the
 * terminal and the finished prompt once the sheet has slid away.
 */
@Composable
fun AgentAskSheet(
    context: AskContext,
    terminals: List<TerminalInfo>,
    onSend: (TerminalInfo, String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheet = rememberModalSheetState()
    var instruction by rememberSaveable { mutableStateOf("") }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = terminals.firstOrNull { it.id == picked } ?: defaultTerminal(terminals)
    val canSend = instruction.isNotBlank() && selected != null
    ModalSheet(onDismissRequest = onDismiss, modifier = modifier, state = sheet) {
        LpmTopBar(
            title = stringResource(R.string.git_ask_title),
            modifier = Modifier.padding(top = 16.dp),
            windowInsets = WindowInsets(0),
            leading = {
                GlassCapsuleButton(
                    text = stringResource(R.string.cancel),
                    onClick = { sheet.dismiss(onDismiss) }
                )
            },
            trailing = {
                GlassCapsuleButton(
                    text = stringResource(R.string.git_ask_send),
                    onClick = {
                        val term = selected ?: return@GlassCapsuleButton
                        val prompt = agentPrompt(instruction, context.path, context.diffText)
                        sheet.dismiss { onSend(term, prompt) }
                    },
                    enabled = canSend,
                    textStyle = LpmTheme.typography.headline
                )
            }
        )
        if (terminals.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = stringResource(R.string.git_ask_no_terminals),
                    icon = painterResource(DesignR.drawable.ic_terminal),
                    description = {
                        Text(
                            text = stringResource(R.string.git_ask_no_terminals_message),
                            style = LpmTheme.typography.body,
                            color = LpmTheme.colors.secondaryLabel
                        )
                    }
                )
            }
        } else {
            AskForm(
                context = context,
                terminals = terminals,
                selected = selected,
                instruction = instruction,
                onInstruction = { instruction = it },
                onPick = { picked = it.id }
            )
        }
    }
}

@Composable
private fun AskForm(
    context: AskContext,
    terminals: List<TerminalInfo>,
    selected: TerminalInfo?,
    instruction: String,
    onInstruction: (String) -> Unit,
    onPick: (TerminalInfo) -> Unit
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(top = ListDefaults.ContentTopPadding, bottom = 24.dp)
    ) {
        ListSection(
            header = stringResource(R.string.git_ask_instruction),
            headerStyle = ListHeaderStyle.Title
        ) {
            TextFieldRow(
                value = instruction,
                onValueChange = onInstruction,
                placeholder = stringResource(R.string.git_ask_instruction_placeholder),
                minLines = 2,
                maxLines = 6,
                fieldModifier = Modifier.focusRequester(focus)
            )
        }
        ListSection(
            header = stringResource(R.string.git_ask_terminal),
            headerStyle = ListHeaderStyle.Title
        ) {
            terminals.forEach { term ->
                val label = if (term.emoji.isEmpty()) term.label else "${term.emoji} ${term.label}"
                ListRow(
                    onClick = { onPick(term) },
                    trailing = {
                        if (term.id == selected?.id) {
                            Icon(
                                painter = painterResource(DesignR.drawable.ic_check),
                                contentDescription = null,
                                tint = LpmTheme.colors.tint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                ) {
                    Text(
                        text = label,
                        style = LpmTheme.typography.body,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        ListSection(
            header = stringResource(R.string.git_ask_context),
            headerStyle = ListHeaderStyle.Title
        ) {
            ContextPreview(context)
        }
    }
}

/** The file's path over its diff, scrolling sideways. */
@Composable
private fun ContextPreview(context: AskContext) {
    val colors = LpmTheme.colors
    val preview = when (val p = previewLines(context.diffText)) {
        PreviewDiff.Empty -> stringResource(R.string.git_ask_no_diff)
        is PreviewDiff.Whole -> p.text
        is PreviewDiff.Cut -> p.text + "\n" + stringResource(R.string.git_ask_more_lines, p.more)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = context.path,
            style = LpmTheme.typography.footnote.copy(fontWeight = FontWeight.SemiBold),
            color = colors.secondaryLabel,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis
        )
        Text(
            text = preview,
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            color = colors.secondaryLabel,
            softWrap = false,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        )
    }
}
