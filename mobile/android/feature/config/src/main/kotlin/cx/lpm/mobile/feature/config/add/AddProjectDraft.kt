package cx.lpm.mobile.feature.config.add

import cx.lpm.mobile.core.model.SshHostInfo
import cx.lpm.mobile.core.model.trimmingWhitespace
import java.text.BreakIterator
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The three ways to add a project, as the desktop offers them. */
enum class AddProjectMode { Local, Clone, Ssh }

/** The host picker's "Enter manually" choice; "" is nothing picked yet. */
const val MANUAL_HOST_PICK = "__manual__"

/**
 * What the add-project form holds for all three modes, so switching modes keeps what
 * was typed. [nameEdited] latches once the name is typed over, and from then on the
 * name no longer follows the folder, repository or host.
 */
data class AddProjectDraft(
    val localRoot: String = "",
    val cloneUrl: String = "",
    val cloneParent: String = "",
    val branch: String = "",
    val hostPick: String = "",
    val host: String = "",
    val user: String = "",
    val port: String = "22",
    val identityFile: String = "",
    val remoteDir: String = "",
    val name: String = "",
    val nameEdited: Boolean = false
) {
    fun isComplete(mode: AddProjectMode): Boolean {
        val fields = when (mode) {
            AddProjectMode.Local -> listOf(localRoot)
            AddProjectMode.Clone -> listOf(cloneUrl, cloneParent)
            AddProjectMode.Ssh -> listOf(host, user)
        }
        return fields.all { it.trimmingWhitespace().isNotEmpty() } && slugify(name).isNotEmpty()
    }

    fun suggestedName(mode: AddProjectMode): String = when (mode) {
        AddProjectMode.Local -> slugify(lastPathComponent(localRoot))

        AddProjectMode.Clone -> slugify(repoName(cloneUrl))

        AddProjectMode.Ssh -> {
            val u = user.trimmingWhitespace()
            val h = host.trimmingWhitespace()
            when {
                u.isEmpty() && h.isEmpty() -> ""
                u.isEmpty() || h.isEmpty() -> slugify(u + h)
                else -> slugify("$u-$h")
            }
        }
    }

    /** The name follows the form until it's been typed over. */
    fun rederived(mode: AddProjectMode): AddProjectDraft =
        if (nameEdited) this else copy(name = suggestedName(mode))

    /**
     * A saved host fills in its alias (not its address, so alias-scoped options still
     * apply), user, port and key; "Enter manually" clears them.
     */
    fun pickingHost(pick: String, hosts: List<SshHostInfo>): AddProjectDraft {
        val picked = copy(hostPick = pick)
        if (pick.isEmpty()) return picked.rederived(AddProjectMode.Ssh)
        if (pick == MANUAL_HOST_PICK) {
            return picked.copy(host = "", user = "", port = "22", identityFile = "")
                .rederived(AddProjectMode.Ssh)
        }
        val match = hosts.firstOrNull { it.name == pick } ?: return picked
        return picked.copy(
            host = match.name,
            user = match.user,
            port = if (match.port > 0) match.port.toString() else "22",
            identityFile = match.identityFile
        ).rederived(AddProjectMode.Ssh)
    }

    /** Typing a different host than the picked one drops back to manual entry. */
    fun editingHost(value: String, hosts: List<SshHostInfo>): AddProjectDraft {
        val edited = copy(host = value)
        val detached = if (hostPick.isNotEmpty() && hostPick != MANUAL_HOST_PICK) {
            val expected = hosts.firstOrNull { it.name == hostPick }?.name.orEmpty()
            if (value != expected) edited.copy(hostPick = MANUAL_HOST_PICK) else edited
        } else {
            edited
        }
        return detached.rederived(AddProjectMode.Ssh)
    }

    /** The connection map `createSshProject` sends. */
    fun sshPayload(): JsonObject = buildJsonObject {
        put("host", host.trimmingWhitespace())
        put("user", user.trimmingWhitespace())
        put("port", port.trimmingWhitespace().toIntOrNull() ?: DEFAULT_SSH_PORT)
        put("key", identityFile.trimmingWhitespace())
        put("dir", remoteDir.trimmingWhitespace())
    }

    private companion object {
        const val DEFAULT_SSH_PORT = 22
    }
}

/**
 * Lower-cases and collapses each run of other characters to one hyphen, trimmed from
 * both ends — the desktop's `slugify`, so the Mac accepts the name it makes.
 */
fun slugify(s: String): String {
    val lowered = s.trimmingWhitespace().lowercase()
    val mapped = StringBuilder()
    var inGap = false
    val characters = BreakIterator.getCharacterInstance().apply { setText(lowered) }
    var start = characters.first()
    var end = characters.next()
    while (end != BreakIterator.DONE) {
        val ch = lowered.substring(start, end)
        if (ch.length == 1 && ch[0].isSlugChar()) {
            mapped.append(ch)
            inGap = false
        } else if (!inGap) {
            mapped.append('-')
            inGap = true
        }
        start = end
        end = characters.next()
    }
    return mapped.split('-').filter { it.isNotEmpty() }.joinToString("-")
}

private fun Char.isSlugChar(): Boolean =
    (this in 'a'..'z') || (this in 'A'..'Z') || (this in '0'..'9') || this in "_.-"

internal fun lastPathComponent(path: String): String {
    val trimmed = path.removeSuffix("/")
    return trimmed.split('/').lastOrNull { it.isNotEmpty() } ?: trimmed
}

/**
 * A git URL's repository name: no trailing slash or `.git`, then the last segment of
 * either a URL or an scp-like `git@host:group/repo`.
 */
internal fun repoName(url: String): String {
    val s = url.trimmingWhitespace().removeSuffix("/").removeSuffix(".git")
    val slash = s.lastIndexOf('/')
    if (slash >= 0) return s.substring(slash + 1)
    val colon = s.lastIndexOf(':')
    return if (colon >= 0) s.substring(colon + 1) else s
}
