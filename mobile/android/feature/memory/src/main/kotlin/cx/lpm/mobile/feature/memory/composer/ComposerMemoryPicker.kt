package cx.lpm.mobile.feature.memory.composer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.EmptyState
import cx.lpm.mobile.core.designsystem.component.GlassCapsuleButton
import cx.lpm.mobile.core.designsystem.component.ListDefaults
import cx.lpm.mobile.core.designsystem.component.ListHeaderStyle
import cx.lpm.mobile.core.designsystem.component.ListSection
import cx.lpm.mobile.core.designsystem.component.LpmTopBar
import cx.lpm.mobile.core.designsystem.component.SearchField
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.memory.R

/** What the picker's rows and bar do. */
@Immutable
class PickerActions(
    val cancel: () -> Unit = {},
    val remember: () -> Unit = {},
    val pick: (PickerRowUi) -> Unit = {},
    val preview: (PickerRowUi) -> Unit = {},
    val delete: (PickerRowUi) -> Unit = {},
    val search: (String) -> Unit = {}
)

/**
 * The picker page (iOS `ComposerMemorySheet`'s list): "Remember this conversation"
 * first, then the saved sessions to continue, searchable once there are enough.
 */
@Composable
fun ComposerMemoryPicker(
    state: ComposerMemoryUiState,
    modifier: Modifier = Modifier,
    actions: PickerActions = PickerActions()
) {
    Column(modifier.fillMaxSize()) {
        LpmTopBar(
            title = stringResource(R.string.memory_title),
            modifier = Modifier.padding(top = 16.dp),
            windowInsets = WindowInsets(0),
            leading = {
                GlassCapsuleButton(text = stringResource(R.string.cancel), onClick = actions.cancel)
            }
        )
        if (state.searchable) {
            SearchField(
                query = state.query,
                onQueryChange = actions.search,
                placeholder = stringResource(R.string.memory_picker_search),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
            )
        }
        Box(Modifier.weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = ListDefaults.HeaderlessContentTopPadding,
                    bottom = 24.dp
                )
            ) {
                item(key = "remember") {
                    ListSection { RememberRow(actions.remember) }
                }
                if (state.sessions.isNotEmpty()) {
                    item(key = "sessions") {
                        ListSection(
                            header = if (state.searchable) {
                                null
                            } else {
                                stringResource(
                                    R.string.memory_picker_continue
                                )
                            },
                            headerStyle = ListHeaderStyle.Title
                        ) {
                            state.visible.forEach { row ->
                                key(row.name) {
                                    PickerSessionRow(
                                        row = row,
                                        onPick = { actions.pick(row) },
                                        onPreview = { actions.preview(row) },
                                        onDelete = { actions.delete(row) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            PickerOverlay(state)
        }
    }
}

@Composable
private fun PickerOverlay(state: ComposerMemoryUiState) {
    val (title, description, icon) = when {
        state.sessions.isEmpty() && !state.loading -> Triple(
            stringResource(R.string.memory_picker_empty),
            stringResource(R.string.memory_picker_empty_description),
            DesignR.drawable.ic_psychology
        )

        state.sessions.isNotEmpty() && state.visible.isEmpty() -> Triple(
            stringResource(R.string.memory_picker_no_results, state.query),
            stringResource(R.string.memory_picker_no_results_description),
            DesignR.drawable.ic_search
        )

        else -> return
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            title = title,
            icon = painterResource(icon),
            description = {
                Text(
                    text = description,
                    style = LpmTheme.typography.body,
                    color = LpmTheme.colors.secondaryLabel,
                    textAlign = TextAlign.Center
                )
            }
        )
    }
}
