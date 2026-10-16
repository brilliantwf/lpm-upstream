package cx.lpm.mobile.feature.config.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.DisclosureChevron
import cx.lpm.mobile.core.designsystem.component.ListDefaults
import cx.lpm.mobile.core.designsystem.component.ListHeaderStyle
import cx.lpm.mobile.core.designsystem.component.ListRow
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.component.SheetScaffold
import cx.lpm.mobile.core.designsystem.component.SwipeAction
import cx.lpm.mobile.core.designsystem.component.SwipeActionsBox
import cx.lpm.mobile.core.designsystem.component.listSeparator
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.model.Action
import cx.lpm.mobile.feature.config.R

/** Which editor the configuration sheet has open over it. */
sealed interface ConfigEditor {
    data object AddService : ConfigEditor

    data class EditService(val key: String) : ConfigEditor

    data object AddProfile : ConfigEditor

    data class EditProfile(val name: String) : ConfigEditor

    data object AddAction : ConfigEditor

    data class EditAction(val key: String) : ConfigEditor

    data object Yaml : ConfigEditor
}

/**
 * The project's services, profiles and actions, each row opening its editor and
 * swiping to delete, then the way into the raw configuration file.
 */
@Composable
fun ProjectConfigScreen(
    state: ProjectConfigUiState,
    onDone: () -> Unit,
    onOpen: (ConfigEditor) -> Unit,
    onDeleteService: (String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onDeleteAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    SheetScaffold(
        title = stringResource(R.string.config_title),
        onDone = onDone,
        modifier = modifier,
        doneLabel = stringResource(R.string.done)
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(top = ListDefaults.ContentTopPadding, bottom = 24.dp)
        ) {
            ConfigListSection(
                header = stringResource(R.string.config_services),
                addTitle = stringResource(R.string.config_add_service),
                onAdd = { onOpen(ConfigEditor.AddService) }
            ) {
                state.services.forEach { service ->
                    DeletableRow(
                        title = service.name,
                        subtitle = if (service.port > 0) {
                            stringResource(R.string.config_port, service.port)
                        } else {
                            null
                        },
                        onClick = { onOpen(ConfigEditor.EditService(service.name)) },
                        onDelete = { onDeleteService(service.name) }
                    )
                }
            }
            ConfigListSection(
                header = stringResource(R.string.config_profiles),
                addTitle = stringResource(R.string.config_add_profile),
                onAdd = { onOpen(ConfigEditor.AddProfile) }
            ) {
                state.profiles.forEach { profile ->
                    DeletableRow(
                        title = profile.name,
                        subtitle = pluralStringResource(
                            R.plurals.config_service_count,
                            profile.services.size,
                            profile.services.size
                        ),
                        onClick = { onOpen(ConfigEditor.EditProfile(profile.name)) },
                        onDelete = { onDeleteProfile(profile.name) }
                    )
                }
            }
            ConfigListSection(
                header = stringResource(R.string.config_actions),
                addTitle = stringResource(R.string.config_add_action),
                onAdd = { onOpen(ConfigEditor.AddAction) }
            ) {
                state.actions.forEach { action ->
                    DeletableRow(
                        title = actionTitle(action),
                        subtitle = if (action.isRunnable) {
                            null
                        } else {
                            stringResource(
                                R.string.config_menu
                            )
                        },
                        onClick = { onOpen(ConfigEditor.EditAction(action.name)) },
                        onDelete = { onDeleteAction(action.name) }
                    )
                }
            }
            ListSection {
                DisclosureRow(
                    title = stringResource(R.string.config_edit_file),
                    subtitle = null,
                    onClick = { onOpen(ConfigEditor.Yaml) }
                )
            }
        }
    }
}

@Composable
private fun ConfigListSection(
    header: String,
    addTitle: String,
    onAdd: () -> Unit,
    rows: @Composable () -> Unit
) {
    ListSection(header = header, headerStyle = ListHeaderStyle.Title) {
        rows()
        ListRow(
            onClick = onAdd,
            leading = {
                Icon(
                    painter = painterResource(DesignR.drawable.ic_add),
                    contentDescription = null,
                    tint = LpmTheme.colors.tint,
                    modifier = Modifier.size(24.dp)
                )
            }
        ) {
            Text(addTitle, style = LpmTheme.typography.body, color = LpmTheme.colors.tint)
        }
    }
}

@Composable
private fun DeletableRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    SwipeActionsBox(
        modifier = Modifier.listSeparator(),
        trailing = listOf(
            SwipeAction(
                label = stringResource(R.string.delete),
                icon = DesignR.drawable.ic_delete,
                color = LpmTheme.colors.red
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.Reject)
                onDelete()
            }
        )
    ) {
        DisclosureRow(title = title, subtitle = subtitle, onClick = onClick)
    }
}

/** A title with an optional caption under it, and the disclosure chevron. */
@Composable
private fun DisclosureRow(title: String, subtitle: String?, onClick: () -> Unit) {
    ListRow(onClick = onClick, trailing = { DisclosureChevron() }) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = LpmTheme.typography.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = LpmTheme.typography.caption1,
                    color = LpmTheme.colors.secondaryLabel
                )
            }
        }
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun actionTitle(action: Action): String = if (action.emoji.isEmpty()) {
    action.label
} else {
    stringResource(R.string.config_action_title, action.emoji, action.label)
}
