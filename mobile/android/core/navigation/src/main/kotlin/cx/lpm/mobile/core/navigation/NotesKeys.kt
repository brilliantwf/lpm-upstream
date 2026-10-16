package cx.lpm.mobile.core.navigation

import androidx.navigation3.runtime.NavKey
import cx.lpm.mobile.core.model.SessionKey
import kotlinx.serialization.Serializable

/** A project's notes. */
@Serializable
data class NotesKey(val project: String, val session: SessionKey = SessionKey.Live) : NavKey

/**
 * One notebook chat. The title rides along so the thread's bar is right before its
 * first page lands.
 */
@Serializable
data class NoteChatKey(
    val project: String,
    val chatId: String,
    val title: String,
    val session: SessionKey = SessionKey.Live
) : NavKey
