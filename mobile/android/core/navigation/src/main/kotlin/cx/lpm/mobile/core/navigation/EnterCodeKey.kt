package cx.lpm.mobile.core.navigation

import androidx.navigation3.runtime.NavKey
import cx.lpm.mobile.core.model.DEFAULT_MAC_PORT
import kotlinx.serialization.Serializable

/** Enter a Code, with the address filled in when a nearby Mac was picked. */
@Serializable
data class EnterCodeKey(val host: String = "", val port: Int = DEFAULT_MAC_PORT) : NavKey
