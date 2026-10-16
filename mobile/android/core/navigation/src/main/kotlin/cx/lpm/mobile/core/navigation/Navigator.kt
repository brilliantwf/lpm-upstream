package cx.lpm.mobile.core.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/** Pushes and pops one root's back stack, like SwiftUI's `NavigationPath`. */
class Navigator(private val backStack: NavBackStack<NavKey>) {
    val current: NavKey? get() = backStack.lastOrNull()

    fun navigate(key: NavKey) {
        backStack.add(key)
    }

    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    /** Pops the first key above the root that matches, and everything above it. */
    fun popFrom(matches: (NavKey) -> Boolean) {
        val index = backStack.indexOfFirst(matches)
        if (index < 1) return
        while (backStack.size > index) backStack.removeAt(backStack.lastIndex)
    }

    /** Replaces everything above the root, e.g. to deep-link from a notification. */
    fun reset(vararg path: NavKey) {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        backStack.addAll(path)
    }
}
