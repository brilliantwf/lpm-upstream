package cx.lpm.mobile.core.data.terminals

/** What a terminal screen hands the store so the stream and composer reach its web view. */
class TerminalHooks(
    val seed: (cols: Int, rows: Int, data: String, reset: Boolean) -> Unit,
    val output: (String) -> Unit,
    val submit: (String) -> Unit,
    val capture: (lines: Int, done: (String) -> Unit) -> Unit
)

/**
 * The screens showing each terminal, oldest first. Two can show the same terminal
 * for a moment — a notification pushes a fresh copy while the one opened from the
 * list is still closing — so the hooks follow the newest copy, and only the last
 * copy to close lets the stream go.
 */
class TerminalScreens {
    enum class Closed {
        /** An older copy closed; the newest one keeps the stream. */
        Covered,

        /** The newest copy closed and an older one is showing again. */
        Revealed,

        /** No screen shows the terminal any more. */
        Last
    }

    private val stacks = mutableMapOf<String, MutableList<Pair<Any, TerminalHooks>>>()

    fun current(id: String): TerminalHooks? = stacks[id]?.lastOrNull()?.second

    fun open(id: String, key: Any, hooks: TerminalHooks) {
        stacks.getOrPut(id) { mutableListOf() } += key to hooks
    }

    fun close(id: String, key: Any): Closed {
        val stack = stacks[id] ?: return Closed.Last
        val index = stack.indexOfFirst { it.first === key }
        if (index < 0) return Closed.Covered
        val wasNewest = index == stack.lastIndex
        stack.removeAt(index)
        if (stack.isEmpty()) {
            stacks.remove(id)
            return Closed.Last
        }
        return if (wasNewest) Closed.Revealed else Closed.Covered
    }
}
