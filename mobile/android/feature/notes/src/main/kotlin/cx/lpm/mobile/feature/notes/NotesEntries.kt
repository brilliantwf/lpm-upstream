package cx.lpm.mobile.feature.notes

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import cx.lpm.mobile.core.navigation.Navigator
import cx.lpm.mobile.core.navigation.NoteChatKey
import cx.lpm.mobile.core.navigation.NotesKey
import cx.lpm.mobile.feature.notes.chat.NoteChatRoute
import cx.lpm.mobile.feature.notes.list.NotesRoute

/** A project's notebook and one chat's thread. */
fun EntryProviderScope<NavKey>.notesEntries(navigator: Navigator) {
    entry<NotesKey> { key ->
        NotesRoute(key = key, onNavigate = navigator::navigate, onBack = navigator::back)
    }
    entry<NoteChatKey> { key ->
        NoteChatRoute(key = key, onBack = navigator::back)
    }
}
