package cx.lpm.mobile.feature.config.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType

/** Names, paths and hosts: no autocorrect, no capitals. */
val PlainKeyboard = KeyboardOptions(
    capitalization = KeyboardCapitalization.None,
    autoCorrectEnabled = false
)

/** A web or git address (`.keyboardType(.URL)`). */
val UrlKeyboard = PlainKeyboard.copy(keyboardType = KeyboardType.Uri)
