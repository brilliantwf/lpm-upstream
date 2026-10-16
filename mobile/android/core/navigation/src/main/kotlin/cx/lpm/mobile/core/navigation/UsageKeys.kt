package cx.lpm.mobile.core.navigation

import androidx.navigation3.runtime.NavKey
import cx.lpm.mobile.core.model.SessionKey
import kotlinx.serialization.Serializable

/** The agents' token usage over time. */
@Serializable
data class StatsKey(val session: SessionKey = SessionKey.Live) : NavKey

/** Each AI account's limits and how fast they're being used. */
@Serializable
data class UsageKey(val session: SessionKey = SessionKey.Live) : NavKey
