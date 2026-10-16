package cx.lpm.mobile.core.model

/**
 * Which Claude account new sessions run on and the rest of the pool they can switch
 * to — the desktop's `useProjectClaudeAccounts`, which tags the usage rows "in use"
 * and "pool". On the projects list nothing is selected, so the rows speak for the
 * main accounts; a project's screen speaks for that project.
 */
data class ClaudeInUse(
    /** The account's limits key; "default" is the main login. */
    val current: String,
    val standby: List<String>,
    /** The project the tags speak for, null for the main accounts. */
    val project: Project?,
    /** Pinned to one account, by the project's own keys or its parent's. */
    val pinned: Boolean
) {
    fun note(account: String): String? {
        val place = project?.let { "in ${it.label}" } ?: "on the main accounts"
        if (account == current) return "New Claude sessions $place use this account"
        if (account in standby) {
            return project?.let { "${it.label} can switch to this account" }
                ?: "The main accounts can switch to this account"
        }
        return null
    }
}

private sealed interface ClaudeSource {
    data class Pin(val id: String) : ClaudeSource

    data class Pool(val owner: String) : ClaudeSource

    data object Main : ClaudeSource
}

/**
 * Mirrors the desktop's `projectAccount` / `projectPool`: a copy with no keys of its
 * own follows its parent's, and a list with no known accounts falls back to the main
 * accounts. Null for an SSH project, whose agents run under the far host's login.
 */
fun ClaudeAccounts.inUse(project: Project?, parent: Project?): ClaudeInUse? {
    if (project?.isRemote == true) return null
    return when (val source = project?.ownSource() ?: parent?.ownSource() ?: ClaudeSource.Main) {
        is ClaudeSource.Pin -> {
            val resolved = if (source.id.isEmpty() || account(source.id) == null) {
                CLAUDE_MAIN_LOGIN
            } else {
                source.id
            }
            ClaudeInUse(resolved, emptyList(), project, pinned = true)
        }

        is ClaudeSource.Pool -> {
            val owner = source.owner
            val known = switching.current[owner] != null || switching.members[owner] != null
            pool(if (known) owner else "", project)
        }

        ClaudeSource.Main -> pool("", project)
    }
}

private fun ClaudeAccounts.pool(key: String, project: Project?): ClaudeInUse {
    val current = switching.current[key] ?: CLAUDE_MAIN_LOGIN
    val standby = if (switching.active) {
        switching.members[key].orEmpty().filter { it != current }
    } else {
        emptyList()
    }
    return ClaudeInUse(current, standby, project, pinned = false)
}

private fun Project.ownSource(): ClaudeSource? {
    claude.account?.let { return ClaudeSource.Pin(it) }
    val list = claude.accounts ?: return null
    return if (list.isEmpty()) ClaudeSource.Main else ClaudeSource.Pool(name)
}
