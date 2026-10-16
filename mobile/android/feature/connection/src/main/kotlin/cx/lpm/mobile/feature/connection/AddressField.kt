package cx.lpm.mobile.feature.connection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cx.lpm.mobile.core.data.link.AddressCheck
import cx.lpm.mobile.core.data.link.AddressKind
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.PlainTextField
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import kotlin.math.roundToInt

/**
 * An editable address with its kind under it and its check result beside it. While
 * [editing], a red button removes it and the handle drags it to another place.
 */
@Composable
internal fun AddressField(
    field: HostField,
    index: Int,
    editing: Boolean,
    inUse: Boolean,
    testing: Boolean,
    check: AddressCheck?,
    onChange: (String) -> Unit,
    onRemove: () -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LpmTheme.colors
    var drag by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(1) }
    ListRow(
        modifier = modifier
            .onSizeChanged { height = it.height.coerceAtLeast(1) }
            .offset { IntOffset(0, drag.roundToInt()) }
            .zIndex(if (drag != 0f) 1f else 0f),
        leading = if (editing) {
            {
                Icon(
                    painter = painterResource(DesignR.drawable.ic_do_not_disturb_on_fill),
                    contentDescription = stringResource(R.string.delete),
                    tint = colors.red,
                    modifier = Modifier
                        .size(26.dp)
                        .clickable(role = Role.Button, onClick = onRemove)
                )
            }
        } else {
            null
        },
        trailing = {
            if (field.host.isNotEmpty()) {
                Spacer(Modifier.width(10.dp))
                AddressResult(inUse, testing, check, showsUnchecked = false)
            }
            if (editing) {
                Spacer(Modifier.width(12.dp))
                Icon(
                    painter = painterResource(DesignR.drawable.ic_drag_handle),
                    contentDescription = stringResource(R.string.reorder),
                    tint = colors.tertiaryLabel,
                    modifier = Modifier
                        .size(24.dp)
                        .pointerInput(index) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    onMove(index, index + (drag / height).roundToInt())
                                    drag = 0f
                                },
                                onDragCancel = { drag = 0f }
                            ) { _, dy -> drag += dy }
                        }
                )
            }
        }
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PlainTextField(
                value = field.text,
                onValueChange = onChange,
                placeholder = stringResource(R.string.address),
                textStyle = LpmTheme.typography.body.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri
                ),
                modifier = Modifier.fillMaxWidth()
            )
            if (field.host.isNotEmpty()) {
                Text(
                    AddressKind.of(field.host).label,
                    style = LpmTheme.typography.caption1,
                    color = colors.secondaryLabel
                )
            }
        }
    }
}
