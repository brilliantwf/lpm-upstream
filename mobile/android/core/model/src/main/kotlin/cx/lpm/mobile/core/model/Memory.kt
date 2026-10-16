package cx.lpm.mobile.core.model

import kotlinx.serialization.json.JsonObject

// Session memory wire types. Timestamps here are unix SECONDS — every Notes
// timestamp is milliseconds.

/**
 * One work-session log. `content` is a 1200-character preview on a `memory` list
 * and the full markdown on a `memorySession` fetch; `size` is always the document's
 * true byte length.
 */
data class MemorySession(
    val name: String,
    val title: String,
    val path: String,
    // Unix seconds.
    val updatedAt: Long,
    val size: Int,
    val content: String
) {
    val id: String get() = name

    companion object {
        fun parse(o: JsonObject): MemorySession = MemorySession(
            name = o.string("name").orEmpty(),
            title = o.string("title").orEmpty(),
            path = o.string("path").orEmpty(),
            updatedAt = o.long("updatedAt") ?: 0,
            size = o.int("size") ?: 0,
            content = o.string("content").orEmpty()
        )
    }
}

/**
 * The `memory` reply: the sessions plus the folder they live in. `exists` false
 * with a filled `dir` is the empty state, not a failure. A duplicate resolves to its
 * original's folder, so `owner` is read off `dir` rather than the project.
 */
data class MemoryList(val exists: Boolean, val dir: String, val sessions: List<MemorySession>) {
    // URL(fileURLWithPath:).lastPathComponent: trailing slashes don't count.
    val owner: String
        get() = dir.trimEnd('/').substringAfterLast('/').ifEmpty {
            if (dir.startsWith("/")) "/" else ""
        }

    companion object {
        fun parse(o: JsonObject): MemoryList = MemoryList(
            exists = o.bool("exists") ?: false,
            dir = o.string("dir").orEmpty(),
            sessions = o.list("sessions", MemorySession::parse)
        )
    }
}
