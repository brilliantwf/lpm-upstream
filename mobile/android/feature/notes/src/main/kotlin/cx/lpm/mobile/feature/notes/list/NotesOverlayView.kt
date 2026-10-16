package cx.lpm.mobile.feature.notes.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.EmptyState
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.feature.notes.R

/** The spinner, a failure, no matches, or the empty notebook, over the list. */
@Composable
internal fun NotesOverlayView(overlay: NotesOverlay, modifier: Modifier = Modifier) {
    if (overlay == NotesOverlay.None) return
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (overlay) {
            NotesOverlay.None -> Unit

            NotesOverlay.Loading -> ActivityIndicator()

            is NotesOverlay.SearchFailed -> Unavailable(
                title = stringResource(R.string.notes_search_failed),
                icon = painterResource(DesignR.drawable.ic_warning),
                description = overlay.message
            )

            is NotesOverlay.NoResults -> Unavailable(
                title = stringResource(R.string.notes_no_results, overlay.query),
                icon = painterResource(DesignR.drawable.ic_search),
                description = stringResource(R.string.notes_no_results_hint)
            )

            is NotesOverlay.Unavailable -> Unavailable(
                title = stringResource(R.string.notes_unavailable),
                icon = painterResource(DesignR.drawable.ic_warning),
                description = overlay.message
            )

            NotesOverlay.Empty -> Unavailable(
                title = stringResource(R.string.notes_empty),
                icon = painterResource(DesignR.drawable.ic_article),
                description = stringResource(R.string.notes_empty_description)
            )
        }
    }
}

@Composable
private fun Unavailable(title: String, icon: Painter, description: String) {
    EmptyState(
        title = title,
        icon = icon,
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
