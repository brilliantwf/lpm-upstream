package cx.lpm.mobile.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The phone's settings: appearance, terminal, notifications, read aloud, Tailscale. */
@Serializable
data object SettingsKey : NavKey
