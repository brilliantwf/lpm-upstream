package cx.lpm.mobile.feature.projects.duplicate

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import cx.lpm.mobile.core.model.Project
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.navigation.PresentationScope

/**
 * The duplicate options sheet wired to the project's session, for another screen to
 * present — the composer's "Run in N duplicates" seeds it with its prompt and copy
 * count. [onDuplicate] runs once the duplicate has gone out.
 */
@Composable
fun DuplicateProjectSheet(
    project: Project,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    session: SessionKey = SessionKey.Live,
    seedPrompt: String? = null,
    seedCount: Int? = null,
    onDuplicate: () -> Unit = {}
) {
    PresentationScope {
        DuplicateProjectContent(
            project = project,
            session = session,
            seedPrompt = seedPrompt,
            seedCount = seedCount,
            onDuplicate = onDuplicate,
            onDismiss = onDismiss,
            modifier = modifier
        )
    }
}

@Composable
private fun DuplicateProjectContent(
    project: Project,
    session: SessionKey,
    seedPrompt: String?,
    seedCount: Int?,
    onDuplicate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DuplicateViewModel = hiltViewModel<DuplicateViewModel, DuplicateViewModel.Factory>(
        creationCallback = { it.create(session) }
    )
) {
    DuplicateOptionsSheet(
        project = project,
        defaults = viewModel.defaults,
        onConfirm = {
            viewModel.duplicate(project, it)
            onDuplicate()
        },
        onDismiss = onDismiss,
        modifier = modifier,
        seedPrompt = seedPrompt,
        seedCount = seedCount
    )
}
