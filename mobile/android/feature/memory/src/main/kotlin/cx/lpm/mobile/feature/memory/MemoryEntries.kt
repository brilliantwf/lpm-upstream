package cx.lpm.mobile.feature.memory

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import cx.lpm.mobile.core.navigation.MemoryKey
import cx.lpm.mobile.core.navigation.MemorySessionKey
import cx.lpm.mobile.core.navigation.Navigator
import cx.lpm.mobile.feature.memory.list.MemoryRoute
import cx.lpm.mobile.feature.memory.session.MemorySessionRoute

/** A project's session memory and one session's document. */
fun EntryProviderScope<NavKey>.memoryEntries(navigator: Navigator) {
    entry<MemoryKey> { key ->
        MemoryRoute(key = key, onNavigate = navigator::navigate, onBack = navigator::back)
    }
    entry<MemorySessionKey> { key ->
        MemorySessionRoute(key = key, onBack = navigator::back)
    }
}
