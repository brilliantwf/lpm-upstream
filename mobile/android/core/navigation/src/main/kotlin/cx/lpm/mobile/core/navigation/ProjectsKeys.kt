package cx.lpm.mobile.core.navigation

import androidx.navigation3.runtime.NavKey
import cx.lpm.mobile.core.model.SessionKey
import kotlinx.serialization.Serializable

/** A project's screen: its tabs, services and the "More" menu. */
@Serializable
data class ProjectKey(val project: String, val session: SessionKey = SessionKey.Live) : NavKey

/** "Add Project…": a new project, an existing folder, a clone or an SSH host. */
@Serializable
data object AddProjectKey : NavKey
