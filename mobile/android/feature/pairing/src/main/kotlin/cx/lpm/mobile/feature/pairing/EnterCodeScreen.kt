package cx.lpm.mobile.feature.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.lpm.mobile.core.data.link.IssueAction
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.GlassIconButton
import cx.lpm.mobile.core.designsystem.component.ListCard
import cx.lpm.mobile.core.designsystem.component.ListDefaults
import cx.lpm.mobile.core.designsystem.component.ListHeaderStyle
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.ListSectionHeader
import cx.lpm.mobile.core.designsystem.component.LpmTopBar
import cx.lpm.mobile.core.designsystem.component.PlainTextField
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * Enter a Code: the pairing code, then the Mac's address with the port beside it,
 * Pair, and what went wrong when it didn't work. [insets] is what the bar keeps
 * clear: the status bar as a screen, nothing inside a sheet.
 */
@Composable
fun EnterCodeScreen(
    form: EnterCodeForm,
    state: EnterCodeUiState,
    onFormChange: (EnterCodeForm) -> Unit,
    onPair: () -> Unit,
    onIssueAction: (IssueAction) -> Boolean,
    onBack: () -> Unit,
    issueCard: IssueCardSlot,
    modifier: Modifier = Modifier,
    insets: WindowInsets? = null
) {
    val codeFocus = remember { FocusRequester() }
    val hostFocus = remember { FocusRequester() }
    val portFocus = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    LaunchedEffect(Unit) { if (form.code.isEmpty()) codeFocus.requestFocus() }
    val pair = {
        if (form.canPair) focus.clearFocus()
        onPair()
    }
    Column(modifier.fillMaxSize().background(LpmTheme.colors.systemGroupedBackground)) {
        LpmTopBar(
            title = stringResource(R.string.enter_code_title),
            windowInsets = insets ?: WindowInsets(0),
            modifier = if (insets == null) Modifier.padding(top = 16.dp) else Modifier,
            leading = {
                GlassIconButton(
                    icon = painterResource(DesignR.drawable.ic_arrow_back_ios_new),
                    contentDescription = stringResource(R.string.back),
                    onClick = onBack
                )
            }
        )
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(top = 19.dp, bottom = 24.dp)
        ) {
            FormSection(header = stringResource(R.string.enter_code_section)) {
                ListRow(verticalPadding = 14.dp) {
                    PlainTextField(
                        value = form.code,
                        onValueChange = { onFormChange(form.copy(code = it)) },
                        placeholder = stringResource(R.string.enter_code_placeholder),
                        textStyle = LpmTheme.typography.title2.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        ),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(onNext = { hostFocus.requestFocus() }),
                        modifier = Modifier.weight(1f).focusRequester(codeFocus)
                    )
                }
            }
            AddressSection(form, onFormChange, pair, hostFocus, portFocus)
            FormSection(header = null) {
                PairRow(
                    enabled = form.canPair && !state.pairing,
                    pairing = state.pairing,
                    onClick = pair
                )
            }
            state.issue?.let { issue ->
                Column(
                    Modifier.padding(horizontal = ListDefaults.Margin),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    issueCard(issue) { action ->
                        val handled = onIssueAction(action)
                        if (handled && action == IssueAction.EnterNewCode) codeFocus.requestFocus()
                        handled
                    }
                    if (state.showDetails) {
                        ListCard {
                            state.checks.keys.sorted().forEach { host ->
                                DetailRow(host, state.checks[host]?.detail.orEmpty())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddressSection(
    form: EnterCodeForm,
    onFormChange: (EnterCodeForm) -> Unit,
    pair: () -> Unit,
    hostFocus: FocusRequester,
    portFocus: FocusRequester
) {
    val colors = LpmTheme.colors
    val footerStyle = LpmTheme.typography.footnote.copy(lineHeight = 16.sp)
    FormSection(
        header = stringResource(R.string.enter_code_address),
        footer = {
            Text(
                stringResource(R.string.enter_code_footer),
                style = footerStyle,
                color = colors.secondaryLabel
            )
            when {
                !form.portIsValid -> Text(
                    stringResource(R.string.enter_code_bad_port),
                    style = footerStyle,
                    color = colors.red
                )

                form.hint == EnterCodeForm.Hint.Tailscale ->
                    Text(
                        stringResource(R.string.enter_code_hint_tailscale),
                        style = footerStyle,
                        color = colors.green
                    )

                form.hint == EnterCodeForm.Hint.Home ->
                    Text(
                        stringResource(R.string.enter_code_hint_home),
                        style = footerStyle,
                        color = colors.orange
                    )
            }
        }
    ) {
        ListRow {
            PlainTextField(
                value = form.host,
                onValueChange = { onFormChange(form.copy(host = it)) },
                placeholder = stringResource(R.string.enter_code_host_placeholder),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { pair() }),
                modifier = Modifier.weight(1f).focusRequester(hostFocus)
            )
        }
        ListRow(
            modifier = Modifier.clickable(
                remember {
                    MutableInteractionSource()
                },
                null
            ) { portFocus.requestFocus() },
            trailing = {
                PlainTextField(
                    value = form.port,
                    onValueChange = { onFormChange(form.copy(port = it)) },
                    placeholder = stringResource(R.string.enter_code_port_placeholder),
                    textAlign = TextAlign.End,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp).focusRequester(portFocus)
                )
            }
        ) {
            Text(stringResource(R.string.port), style = LpmTheme.typography.body)
        }
    }
}

@Composable
private fun PairRow(enabled: Boolean, pairing: Boolean, onClick: () -> Unit) {
    val colors = LpmTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (pairing) ActivityIndicator(size = ActivityIndicatorSize.Small)
        Text(
            stringResource(if (pairing) R.string.enter_code_pairing else R.string.enter_code_pair),
            style = LpmTheme.typography.headline,
            color = if (enabled) colors.tint else colors.tertiaryLabel
        )
    }
}

@Composable
private fun DetailRow(title: String, value: String) {
    val mono = LpmTheme.typography.footnote.copy(fontFamily = FontFamily.Monospace)
    ListRow(trailing = { Text(value, style = mono, color = LpmTheme.colors.secondaryLabel) }) {
        Text(title, style = mono)
    }
}

/** A `Form` section whose footer can hold more than one line, each in its own color. */
@Composable
private fun FormSection(
    header: String?,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(Modifier.padding(horizontal = ListDefaults.Margin)) {
        if (header != null) ListSectionHeader(header, style = ListHeaderStyle.Title)
        ListCard(content = content)
        if (footer != null) {
            Column(
                Modifier.padding(
                    start = ListDefaults.RowPadding,
                    end = ListDefaults.RowPadding,
                    top = 8.8.dp,
                    bottom = 12.5.dp
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) { footer() }
        } else {
            Spacer(Modifier.height(25.6.dp))
        }
    }
}
