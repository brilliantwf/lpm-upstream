package cx.lpm.mobile.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Every agent and automation on the Mac, most urgent first. */
@Serializable
data object ActivityKey : NavKey
