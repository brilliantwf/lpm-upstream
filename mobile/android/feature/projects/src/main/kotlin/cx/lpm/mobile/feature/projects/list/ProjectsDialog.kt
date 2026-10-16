package cx.lpm.mobile.feature.projects.list

import cx.lpm.mobile.core.model.Project
import cx.lpm.mobile.core.model.ProjectFolder
import cx.lpm.mobile.core.model.WorkStatusChoice

/** The one alert, confirmation or sheet the projects list has open. */
sealed interface ProjectsDialog {
    data object NewFolder : ProjectsDialog

    /** "New folder…" from a project's menu: create it and move the project in. */
    data class NewFolderFor(val project: Project) : ProjectsDialog

    data class RenameFolder(val folder: ProjectFolder) : ProjectsDialog

    data class DeleteFolder(val folder: ProjectFolder) : ProjectsDialog

    /** Removing deletes the duplicate's folder from disk, so it's always confirmed. */
    data class RemoveDuplicate(val project: Project) : ProjectsDialog

    data class Duplicate(val project: Project) : ProjectsDialog

    data class WorkStatusNote(val project: Project, val choice: WorkStatusChoice) :
        ProjectsDialog
}
