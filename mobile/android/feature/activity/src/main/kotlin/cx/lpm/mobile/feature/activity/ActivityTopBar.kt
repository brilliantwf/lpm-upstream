package cx.lpm.mobile.feature.activity

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.GlassIconButton
import cx.lpm.mobile.core.designsystem.component.LpmTopBar
import cx.lpm.mobile.core.designsystem.component.SearchField
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * The navigation bar with the search field in its drawer (`.searchable`, always
 * shown). Searching takes the bar's place, as on iOS: the title and its buttons
 * slide away, the field moves up and a Close button joins it; Close (or back)
 * clears the search and brings the bar back.
 */
@Composable
fun ActivityTopBar(
    state: ActivityUiState,
    query: String,
    searching: Boolean,
    actions: ActivityActions,
    modifier: Modifier = Modifier
) {
    val focus = LocalFocusManager.current
    val endSearch = {
        focus.clearFocus()
        actions.endSearch()
    }
    BackHandler(enabled = searching, onBack = endSearch)
    Column(modifier.windowInsetsPadding(WindowInsets.statusBars)) {
        AnimatedVisibility(
            visible = !searching,
            enter = fadeIn(tween(SEARCH_MS)) + expandVertically(tween(SEARCH_MS)),
            exit = fadeOut(tween(SEARCH_MS)) + shrinkVertically(tween(SEARCH_MS))
        ) {
            Column {
                LpmTopBar(
                    title = stringResource(R.string.activity_title),
                    subtitle = state.subtitle,
                    windowInsets = WindowInsets(0),
                    leading = {
                        GlassIconButton(
                            icon = painterResource(DesignR.drawable.ic_arrow_back_ios_new),
                            contentDescription = stringResource(R.string.activity_back),
                            onClick = actions.back
                        )
                    },
                    trailing = { GroupButton(state.grouped, actions.toggleGrouped) }
                )
                Spacer(Modifier.height(9.dp))
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SearchField(
                query = query,
                onQueryChange = actions.search,
                placeholder = stringResource(R.string.activity_search),
                onSearch = { focus.clearFocus() },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { if (it.isFocused) actions.beginSearch() }
            )
            AnimatedVisibility(
                visible = searching,
                enter = fadeIn(tween(SEARCH_MS)) + expandHorizontally(tween(SEARCH_MS)),
                exit = fadeOut(tween(SEARCH_MS)) + shrinkHorizontally(tween(SEARCH_MS))
            ) {
                Row {
                    Spacer(Modifier.width(11.dp))
                    GlassIconButton(
                        icon = painterResource(DesignR.drawable.ic_close),
                        contentDescription = stringResource(R.string.activity_close),
                        onClick = endSearch
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupButton(grouped: Boolean, onClick: () -> Unit) {
    val state =
        stringResource(if (grouped) R.string.activity_group_on else R.string.activity_group_off)
    GlassIconButton(
        icon = painterResource(
            if (grouped) DesignR.drawable.ic_stacks_fill else DesignR.drawable.ic_stacks
        ),
        contentDescription = stringResource(R.string.activity_group_by_project),
        onClick = onClick,
        tint = if (grouped) LpmTheme.colors.tint else LpmTheme.colors.label,
        iconSize = 27.dp,
        modifier = Modifier.semantics { stateDescription = state }
    )
}

/** How long the bar takes to give way to the search, or come back (iOS's ~0.25 s). */
private const val SEARCH_MS = 250
