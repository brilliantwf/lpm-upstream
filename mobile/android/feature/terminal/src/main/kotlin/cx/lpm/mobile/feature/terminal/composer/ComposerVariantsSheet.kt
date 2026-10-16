package cx.lpm.mobile.feature.terminal.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.CapsuleButton
import cx.lpm.mobile.core.designsystem.component.ModalSheet
import cx.lpm.mobile.core.designsystem.component.rememberModalSheetState
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.terminal.R

private val CardShape = RoundedCornerShape(16.dp)
private val EditorShape = RoundedCornerShape(12.dp)

/**
 * The rewrite picker for more than one variant: each arriving variant is an
 * editable card, "Use this" puts it in the composer, and a failed one shows why.
 * Closing without choosing leaves the composer as it was.
 */
@Composable
internal fun ComposerVariantsSheet(
    variants: List<TransformVariant>,
    generating: Boolean,
    onUse: (String) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    // Edits per variant, so typing in one card survives the next one arriving.
    val drafts = remember { mutableStateMapOf<String, String>() }
    val sheet = rememberModalSheetState()
    LpmTheme(darkTheme = true) {
        ModalSheet(onDismissRequest = onDismiss, state = sheet) {
            SheetHeader(
                title = stringResource(R.string.variants_title),
                leading = stringResource(R.string.composer_cancel) to { sheet.dismiss(onCancel) }
            )
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                variants.forEach { variant ->
                    val text = variant.text
                    if (text != null) {
                        VariantCard(
                            index = variant.idx,
                            text = drafts[variant.id] ?: text,
                            onEdit = { drafts[variant.id] = it },
                            onUse = {
                                val picked = drafts[variant.id] ?: text
                                sheet.dismiss { onUse(picked) }
                            }
                        )
                    } else {
                        FailedVariantCard(
                            index = variant.idx,
                            error =
                                variant.error ?: stringResource(R.string.variant_failed_fallback)
                        )
                    }
                }
                if (generating) Generating()
            }
        }
    }
}

@Composable
private fun VariantCard(index: Int, text: String, onEdit: (String) -> Unit, onUse: () -> Unit) {
    val colors = LpmTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.04f), CardShape)
            .border(1.dp, Color.White.copy(alpha = 0.08f), CardShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.variant_title, index + 1),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.secondaryLabel
            )
            Spacer(Modifier.weight(1f))
            CapsuleButton(
                text = stringResource(R.string.variant_use),
                onClick = onUse,
                prominent = true,
                leading = { CapsuleIcon(DesignR.drawable.ic_check_circle_fill) }
            )
        }
        BasicTextField(
            value = text,
            onValueChange = onEdit,
            textStyle = TextStyle(fontSize = 15.sp, color = colors.label),
            cursorBrush = SolidColor(colors.tint),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 90.dp, max = 220.dp)
                .background(Color.White.copy(alpha = 0.06f), EditorShape)
                .padding(8.dp)
        )
    }
}

@Composable
private fun FailedVariantCard(index: Int, error: String) {
    val colors = LpmTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.orange.copy(alpha = 0.08f), CardShape)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painterResource(DesignR.drawable.ic_warning_fill),
            null,
            tint = colors.orange,
            modifier = Modifier.size(20.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.variant_failed, index + 1),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(error, fontSize = 12.sp, color = colors.secondaryLabel, maxLines = 2)
        }
    }
}

@Composable
private fun Generating() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActivityIndicator(
            size = ActivityIndicatorSize.Small,
            color = LpmTheme.colors.secondaryLabel
        )
        Text(
            stringResource(R.string.variants_generating),
            style = LpmTheme.typography.footnote,
            color = LpmTheme.colors.secondaryLabel
        )
    }
}
