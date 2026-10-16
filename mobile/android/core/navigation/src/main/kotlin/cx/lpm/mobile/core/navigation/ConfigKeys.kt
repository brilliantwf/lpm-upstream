package cx.lpm.mobile.core.navigation

import androidx.navigation3.runtime.NavKey
import cx.lpm.mobile.core.model.SessionKey
import kotlinx.serialization.Serializable

/** "Configure…": the project's services, profiles and actions. */
@Serializable
data class ProjectConfigKey(val project: String, val session: SessionKey = SessionKey.Live) : NavKey

/** The raw YAML editor, the one way to fix a configuration the Mac can't read. */
@Serializable
data class YamlConfigKey(val project: String, val session: SessionKey = SessionKey.Live) : NavKey
