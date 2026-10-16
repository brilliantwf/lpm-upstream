package cx.lpm.mobile.core.navigation

import androidx.navigation3.runtime.NavKey
import cx.lpm.mobile.core.model.SessionKey
import kotlinx.serialization.Serializable

/** A project's shared agent memory. */
@Serializable
data class MemoryKey(val project: String, val session: SessionKey = SessionKey.Live) : NavKey

/** One session memory document; `title` shows until the document lands. */
@Serializable
data class MemorySessionKey(
    val project: String,
    val name: String,
    val title: String,
    val session: SessionKey = SessionKey.Live
) : NavKey
