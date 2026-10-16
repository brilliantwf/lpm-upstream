package cx.lpm.mobile.feature.projects.duplicate

import androidx.lifecycle.ViewModel
import cx.lpm.mobile.core.data.projects.ProjectsStore
import cx.lpm.mobile.core.data.session.MacSessions
import cx.lpm.mobile.core.model.DuplicateOptions
import cx.lpm.mobile.core.model.Project
import cx.lpm.mobile.core.model.SessionKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel

/** Duplicates a project with the options its sheet collected. */
@HiltViewModel(assistedFactory = DuplicateViewModel.Factory::class)
class DuplicateViewModel @AssistedInject constructor(
    @Assisted sessionKey: SessionKey,
    sessions: MacSessions
) : ViewModel() {
    private val projects: ProjectsStore? = sessions[sessionKey]?.store()

    /** The toggles the sheet opens with, from the desktop's duplicate settings. */
    val defaults: DuplicateOptions get() = projects?.state?.value?.duplicateDefaults
        ?: DuplicateOptions()

    fun duplicate(project: Project, options: DuplicateOptions) {
        projects?.duplicateProject(project, options)
    }

    @AssistedFactory
    interface Factory {
        fun create(sessionKey: SessionKey): DuplicateViewModel
    }
}
