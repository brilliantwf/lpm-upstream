package cx.lpm.mobile.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The pairing screen, the root while the phone has no saved Mac. */
@Serializable
data object PairingKey : NavKey

/** The projects list, the root once a Mac is saved (or Demo Mode is on). */
@Serializable
data object ProjectsKey : NavKey
