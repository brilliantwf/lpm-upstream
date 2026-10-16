package cx.lpm.mobile.feature.terminal

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import cx.lpm.mobile.core.navigation.Navigator
import cx.lpm.mobile.core.navigation.TerminalKey

fun EntryProviderScope<NavKey>.terminalEntries(
    navigator: Navigator,
    slots: TerminalSlots = TerminalSlots()
) {
    entry<TerminalKey> { key -> TerminalRoute(key, navigator, slots) }
}
