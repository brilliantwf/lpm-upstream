package cx.lpm.mobile.core.protocol

import kotlinx.serialization.json.put

/** Session memory: the per-project work-session logs. */
sealed interface WireMemory {
    fun memory(project: String): String = frame("memory") { put("project", project) }

    fun memorySession(project: String, name: String): String = frame("memorySession") {
        put("project", project)
        put("name", name)
    }

    /**
     * Compare-and-swap save. `baseline` present means "write only if the file still
     * reads exactly like this" — an empty string is a real baseline (the file must
     * not exist yet), so absence, not emptiness, is what overwrites unconditionally.
     */
    fun memorySave(project: String, name: String, content: String, baseline: String?): String =
        frame("memorySave") {
            put("project", project)
            put("name", name)
            put("content", content)
            if (baseline != null) put("baseline", baseline)
        }

    fun memoryDelete(project: String, name: String): String = frame("memoryDelete") {
        put("project", project)
        put("name", name)
    }

    /**
     * Turn a typed title into a session slug the Mac accepts as a filename
     * (`^[a-z0-9][a-z0-9-]{0,63}$`), matching the desktop Memory pane. Returns "" for
     * a title with nothing usable in it.
     */
    fun slugify(raw: String): String {
        val slug = StringBuilder()
        var pendingSeparator = false
        for (ch in raw.lowercase()) {
            if (ch in 'a'..'z' || ch in '0'..'9') {
                if (pendingSeparator && slug.isNotEmpty()) slug.append('-')
                pendingSeparator = false
                slug.append(ch)
            } else {
                pendingSeparator = true
            }
        }
        return slug.take(64).toString()
    }
}
