package cx.lpm.mobile.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * The iOS 26 search field (`.searchable`): a full-width glass capsule with a
 * magnifier, the [placeholder] in gray, and a clear button once there is text.
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {}
) {
    val colors = LpmTheme.colors
    val textStyle = LpmTheme.typography.body.copy(color = colors.label)
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val submit = {
        keyboard?.hide()
        focus.clearFocus()
        onSearch()
    }
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(colors.tint),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // Like iOS, the keyboard's search key (or a hardware Return) ends editing.
        keyboardActions = KeyboardActions(onSearch = { submit() }),
        modifier = modifier
            .onPreviewKeyEvent { event ->
                val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
                if (enter && event.type == KeyEventType.KeyUp) submit()
                enter
            }
            .fillMaxWidth()
            .height(44.dp)
            .glass(CircleShape, colors),
        decorationBox = { field ->
            Row(
                modifier = Modifier.padding(start = 15.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Glyph(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = null,
                    size = 22.dp,
                    bounds = DpSize(16.dp, 16.dp),
                    tint = colors.label
                )
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = colors.secondaryLabel,
                            maxLines = 1
                        )
                    }
                    field()
                }
                if (query.isNotEmpty()) {
                    ClearButton(onClick = { onQueryChange("") })
                }
            }
        }
    )
}

@Composable
private fun ClearButton(onClick: () -> Unit) {
    val target = remember { TouchTarget() }
    Glyph(
        painter = painterResource(R.drawable.ic_cancel_fill),
        contentDescription = "Clear text",
        size = 22.dp,
        bounds = DpSize(19.dp, 19.dp),
        tint = LpmTheme.colors.label,
        modifier = Modifier
            .padding(start = 8.dp)
            .clickableTarget(
                target,
                remember {
                    MutableInteractionSource()
                },
                true,
                Role.Button,
                onClick
            )
    )
}
