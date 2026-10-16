package cx.lpm.mobile.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Which session a screen belongs to: the live Mac's, or a machine opened through it
 * (iOS's second `AppModel`, `AppModel+Visit`). Navigation keys carry it so a screen
 * resolves its stores from the right session (`MacSessions[key]`).
 */
@Serializable
sealed interface SessionKey {
    @Serializable
    @SerialName("live")
    data object Live : SessionKey

    /** A machine visit; `id` is the machine's slug on the live Mac (one visit at a time). */
    @Serializable
    @SerialName("visit")
    data class Visit(val id: String) : SessionKey
}
