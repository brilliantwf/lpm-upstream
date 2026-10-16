package cx.lpm.mobile.feature.terminal.composer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.CapsuleButton
import cx.lpm.mobile.core.designsystem.component.ListDefaults
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.component.ModalSheet
import cx.lpm.mobile.core.designsystem.component.rememberModalSheetState
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.ComposerAction
import cx.lpm.mobile.feature.terminal.R

/** The key the free-form instruction's variant count is remembered under. */
internal const val FREEFORM_KEY = "__freeform__"

/**
 * "Rewrite with AI": the Mac's enabled composer actions plus a free-form
 * instruction, each with a 1–5 variants stepper remembered for the session.
 * Running closes the sheet and starts the rewrite on the composer.
 */
@Composable
internal fun ComposerActionsSheet(
    actions: List<ComposerAction>,
    variants: (String) -> Int,
    onVariantsChange: (key: String, count: Int) -> Unit,
    onRun: (instruction: String, key: String) -> Unit,
    onDismiss: () -> Unit
) {
    var freeform by rememberSaveable { mutableStateOf("") }
    val sheet = rememberModalSheetState()
    val run = { instruction: String, key: String ->
        sheet.dismiss(onDismiss)
        onRun(instruction, key)
    }
    LpmTheme(darkTheme = true) {
        ModalSheet(
            onDismissRequest = onDismiss,
            fullHeight = false,
            state = sheet,
            floating = true
        ) {
            SheetHeader(
                title = stringResource(R.string.rewrite_title),
                leading = stringResource(R.string.composer_cancel) to { sheet.dismiss(onDismiss) }
            )
            Column(
                Modifier.verticalScroll(
                    rememberScrollState()
                ).padding(top = ListDefaults.ContentTopPadding)
            ) {
                if (actions.isNotEmpty()) {
                    ListSection(header = stringResource(R.string.rewrite_quick)) {
                        actions.forEach { action ->
                            ActionRow(
                                action = action,
                                variants = variants(action.id),
                                onVariantsChange = { onVariantsChange(action.id, it) },
                                onRun = { run(action.instruction, action.id) }
                            )
                        }
                    }
                }
                ListSection(header = stringResource(R.string.rewrite_custom)) {
                    ListRow {
                        FreeformField(freeform, onChange = { freeform = it })
                    }
                    ListRow {
                        VariantStepper(variants(FREEFORM_KEY), onChange = {
                            onVariantsChange(FREEFORM_KEY, it)
                        })
                        Spacer(Modifier.weight(1f))
                        CapsuleButton(
                            text = stringResource(R.string.rewrite_run),
                            onClick = { run(freeform, FREEFORM_KEY) },
                            prominent = true,
                            enabled = freeform.isNotBlank(),
                            leading = {
                                val dimmed = LpmTheme.colors.tertiaryLabel
                                CapsuleIcon(
                                    DesignR.drawable.ic_auto_fix_high,
                                    tint = if (freeform.isBlank()) dimmed else Color.White
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FreeformField(value: String, onChange: (String) -> Unit) {
    val colors = LpmTheme.colors
    val style = LpmTheme.typography.body.copy(color = colors.label)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = style,
        cursorBrush = SolidColor(colors.tint),
        maxLines = 4,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { field ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        stringResource(R.string.rewrite_custom_placeholder),
                        style = style,
                        color = colors.placeholderText
                    )
                }
                field()
            }
        }
    )
}

@Composable
private fun ActionRow(
    action: ComposerAction,
    variants: Int,
    onVariantsChange: (Int) -> Unit,
    onRun: () -> Unit
) {
    val colors = LpmTheme.colors
    ListRow {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(actionIcon(action.icon)),
                    null,
                    tint = colors.tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                action.label,
                fontSize = 15.sp,
                color = colors.label,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            VariantStepper(variants, onChange = onVariantsChange)
            Icon(
                painterResource(DesignR.drawable.ic_arrow_circle_right_fill),
                contentDescription = action.label,
                tint = colors.tint,
                modifier = Modifier.size(28.dp).clickable(role = Role.Button, onClick = onRun)
            )
        }
    }
}

/** An action's `icon` key as a symbol; unknown keys get the wand. */
private fun actionIcon(key: String): Int = when (key) {
    "sparkles" -> DesignR.drawable.ic_auto_awesome
    "minimize" -> DesignR.drawable.ic_close_fullscreen
    "spellcheck" -> DesignR.drawable.ic_spellcheck
    "code" -> DesignR.drawable.ic_code
    "zap" -> DesignR.drawable.ic_bolt_fill
    else -> DesignR.drawable.ic_auto_fix_high
}
