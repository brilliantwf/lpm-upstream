package cx.lpm.mobile.feature.terminal.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ContextMenu
import cx.lpm.mobile.core.designsystem.component.ContextMenuItem
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.terminal.R

/** The filter chips: All, Favorites, Drafts and each folder (long-press to delete), then a new folder. */
@Composable
internal fun HistoryFilterBar(
    state: HistoryUiState,
    onFilter: (HistoryFilter) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onNewFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(stringResource(R.string.history_all), null, state.filter == HistoryFilter.All) {
            onFilter(HistoryFilter.All)
        }
        FilterChip(
            stringResource(R.string.history_favorites),
            DesignR.drawable.ic_star_fill,
            state.filter == HistoryFilter.Favorites
        ) { onFilter(HistoryFilter.Favorites) }
        FilterChip(
            stringResource(R.string.history_drafts),
            DesignR.drawable.ic_edit,
            state.filter == HistoryFilter.Drafts
        ) {
            onFilter(HistoryFilter.Drafts)
        }
        state.folders.forEach { folder ->
            FolderChip(
                name = folder.name,
                active = state.filter == HistoryFilter.Folder(folder.id),
                onClick = { onFilter(HistoryFilter.Folder(folder.id)) },
                onDelete = { onDeleteFolder(folder.id) }
            )
        }
        Icon(
            painterResource(DesignR.drawable.ic_create_new_folder),
            contentDescription = stringResource(R.string.history_new_folder),
            tint = LpmTheme.colors.secondaryLabel,
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .size(20.dp)
                .clickable(role = Role.Button, onClick = onNewFolder)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderChip(name: String, active: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        FilterChip(name, DesignR.drawable.ic_folder, active, onLongClick = {
            menu = true
        }, onClick = onClick)
        ContextMenu(expanded = menu, onDismissRequest = { menu = false }) {
            ContextMenuItem(
                title = stringResource(R.string.history_delete_folder),
                icon = painterResource(DesignR.drawable.ic_delete),
                destructive = true,
                onClick = onDelete
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilterChip(
    label: String,
    icon: Int?,
    active: Boolean,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val colors = LpmTheme.colors
    val content = if (active) Color.White else colors.label
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (active) colors.tint else Color.White.copy(alpha = 0.08f))
            .combinedClickable(role = Role.Button, onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon !=
            null
        ) {
            Icon(painterResource(icon), null, tint = content, modifier = Modifier.size(13.dp))
        }
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = content, maxLines = 1)
    }
}
