package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * A `TextField` as a row of a `Form` section: the field fills the row, [minLines] to
 * [maxLines] tall. A field several lines tall from the start keeps iOS's slightly
 * larger margins above and below its text.
 */
@Composable
fun TextFieldRow(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    textStyle: TextStyle = LpmTheme.typography.body,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    maxLines: Int = 1,
    minLines: Int = 1
) {
    ListRow(
        modifier = modifier,
        verticalPadding = if (minLines > 1) MultiLinePadding else ListDefaults.RowVerticalPadding
    ) {
        PlainTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            textStyle = textStyle,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            maxLines = maxLines,
            minLines = minLines,
            modifier = fieldModifier.weight(1f)
        )
    }
}

private val MultiLinePadding = 14.dp
