package cx.lpm.mobile.core.model

/** What to run in each new copy — mirrors the desktop modal's run modes. */
enum class RunMode(val rawValue: String) {
    None("none"),
    Action("action"),
    Command("command")
}

/**
 * The duplicate options, mirroring the desktop "Duplicate project" modal: how many
 * copies (1–50), a per-copy display label, an optional sidebar folder to group
 * them under, the three git toggles, and an optional task to run in each copy (an
 * action or a command, plus a text prompt for AI agents).
 */
data class DuplicateOptions(
    val count: Int = 1,
    val labels: List<String> = listOf(""),
    val groupName: String = "",
    val excludeUncommitted: Boolean = false,
    val pullLatest: Boolean = true,
    val reinstallDeps: Boolean = false,
    val runMode: RunMode = RunMode.None,
    val actionName: String = "",
    val command: String = "",
    val prompt: String = ""
)
