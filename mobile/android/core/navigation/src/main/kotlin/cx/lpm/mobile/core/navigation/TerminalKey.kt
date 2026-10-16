package cx.lpm.mobile.core.navigation

import androidx.navigation3.runtime.NavKey
import cx.lpm.mobile.core.model.SessionKey
import kotlinx.serialization.Serializable

@Serializable
data class TerminalKey(
    val project: String,
    val terminalId: String,
    val session: SessionKey = SessionKey.Live
) : NavKey
