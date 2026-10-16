package cx.lpm.mobile.core.navigation

import androidx.navigation3.runtime.NavKey
import cx.lpm.mobile.core.model.SessionKey
import kotlinx.serialization.Serializable

/** The ⚡ button's sheet: the project's actions, to run one. */
@Serializable
data class ProjectActionsKey(val project: String, val session: SessionKey = SessionKey.Live) :
    NavKey
