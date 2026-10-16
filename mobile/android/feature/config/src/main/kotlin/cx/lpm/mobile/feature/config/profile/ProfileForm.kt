package cx.lpm.mobile.feature.config.profile

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ListHeaderStyle
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.component.TextFieldRow
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.config.R
import cx.lpm.mobile.feature.config.ui.PlainKeyboard

/** The profile form: its name, the services it starts, and Delete when editing. */
@Composable
internal fun ProfileForm(
    state: ProfileEditorUiState,
    onNameChange: (String) -> Unit,
    onToggle: (String) -> Unit,
    onDelete: () -> Unit
) {
    ListSection {
        TextFieldRow(
            value = state.name,
            onValueChange = onNameChange,
            placeholder = stringResource(R.string.config_profile_name),
            keyboardOptions = PlainKeyboard
        )
    }
    ListSection(
        header = stringResource(R.string.config_services),
        headerStyle = ListHeaderStyle.Title,
        footer = stringResource(R.string.config_profile_footer)
    ) {
        if (state.services.isEmpty()) {
            ListRow {
                Text(
                    text = stringResource(R.string.config_profile_no_services),
                    style = LpmTheme.typography.body,
                    color = LpmTheme.colors.secondaryLabel
                )
            }
        }
        state.services.forEach { service ->
            ListRow(
                onClick = { onToggle(service) },
                trailing = {
                    if (service in state.selected) {
                        Icon(
                            painter = painterResource(DesignR.drawable.ic_check),
                            contentDescription = null,
                            tint = LpmTheme.colors.tint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            ) {
                // A Form's Button draws its label in the tint.
                Text(
                    text = service,
                    style = LpmTheme.typography.body,
                    color = LpmTheme.colors.tint,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
    if (state.editing) {
        ListSection {
            ListRow(onClick = onDelete, enabled = !state.saving) {
                Text(
                    text = stringResource(R.string.config_profile_delete),
                    style = LpmTheme.typography.body,
                    color = with(LpmTheme.colors) { if (state.saving) tertiaryLabel else red },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
