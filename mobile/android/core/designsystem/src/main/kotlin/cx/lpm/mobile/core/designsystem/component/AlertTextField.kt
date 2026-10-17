package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * The capsule text field inside an [LpmAlert] ("Name"), focused when the alert opens
 * with the cursor after the text it was seeded with, as an iOS alert's field is.
 */
@Composable
fun AlertTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    // A change from outside (not typed here) puts the cursor at its end.
    val shown = if (field.text == value) field else TextFieldValue(value, TextRange(value.length))
    val colors = LpmTheme.colors
    val style = LpmTheme.typography.body.copy(color = colors.label)
    BasicTextField(
        value = shown,
        onValueChange = {
            field = it
            if (it.text != value) onValueChange(it.text)
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(colors.systemFill, CircleShape)
            .padding(horizontal = 16.dp)
            .wrapContentHeight()
            .focusRequester(focus),
        textStyle = style,
        singleLine = true,
        cursorBrush = SolidColor(colors.tint),
        keyboardOptions = keyboardOptions,
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = style.copy(color = colors.placeholderText),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                inner()
            }
        }
    )
}
