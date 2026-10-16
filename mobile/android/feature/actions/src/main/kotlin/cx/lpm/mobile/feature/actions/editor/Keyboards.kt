package cx.lpm.mobile.feature.actions.editor

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization

/** Names, commands and paths: no autocorrect, no capitals. */
internal val PlainKeyboard = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false
)
