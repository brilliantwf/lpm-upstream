package cx.lpm.mobile.core.demo

/**
 * Demo handlers for Session Memory: the folder listing (previews only), one document in
 * full, compare-and-swap saves, and deletes. Writes land in the world and are followed
 * by a `memory-changed` push, so the live refresh an agent CLI normally triggers happens
 * in the demo too.
 *
 * A duplicate reads and writes its original's folder, exactly as on a Mac, so a project
 * duplicated in Demo Mode opens the sessions it was copied from.
 */
internal fun DemoServer.registerMemoryHandlers() {
    seedMemoryFixtures()

    register("memory") { o ->
        val project = o.string("project") ?: return@register
        pushAfter(0.35) { memoryListPayload(project) }
    }

    register("memorySession") { o ->
        val project = o.string("project") ?: return@register
        val name = o.string("name") ?: return@register
        pushAfter(0.3) {
            val owner = memoryOwner(project)
            val doc = world.memoryDocs[owner]?.firstOrNull { it.name == name }
                ?: return@pushAfter mapOf(
                    "t" to "memorySession",
                    "project" to project,
                    "name" to name,
                    "ok" to false,
                    "error" to "This session was removed."
                )
            val reply = memorySessionDict(doc, owner, preview = false).toMutableMap()
            reply["t"] = "memorySession"
            reply["project"] = project
            reply["ok"] = true
            reply
        }
    }

    register("memorySave") { o ->
        val project = o.string("project") ?: return@register
        val name = o.string("name") ?: return@register
        val content = o.string("content") ?: ""
        val baseline = o.string("baseline")
        pushAfter(0.45) {
            val reply = mutableMapOf<String, Any?>(
                "t" to "memorySave",
                "project" to project,
                "name" to name
            )
            val failure = saveMemoryDoc(project, name, content, baseline)
            if (failure != null) {
                reply["ok"] = false
                reply["error"] = failure
            } else {
                reply["ok"] = true
            }
            reply
        }
    }

    register("memoryDelete") { o ->
        val project = o.string("project") ?: return@register
        val name = o.string("name") ?: return@register
        pushAfter(0.35) {
            val owner = memoryOwner(project)
            val docs = world.memoryDocs[owner]
            if (docs != null && docs.any { it.name == name }) {
                docs.removeAll { it.name == name }
                pushMemoryChanged(owner)
            }
            mapOf("t" to "memoryDelete", "project" to project, "name" to name, "ok" to true)
        }
    }
}

// state

/** The folder a project's sessions live in: its original's when it is a duplicate. */
private fun DemoServer.memoryOwner(project: String): String {
    val parent = world.project(project)?.parentName ?: ""
    return parent.ifEmpty { project }
}

private fun memoryDir(owner: String): String = "~/.lpm/memory/$owner"

private fun DemoServer.memoryListPayload(project: String): Dict {
    val owner = memoryOwner(project)
    val docs = (world.memoryDocs[owner] ?: emptyList())
        .sortedWith(compareByDescending<DemoWorld.MemoryDoc> { it.updatedAt }.thenBy { it.name })
    return mapOf(
        "t" to "memory",
        "project" to project,
        "ok" to true,
        "exists" to (world.memoryDocs[owner] != null),
        "dir" to memoryDir(owner),
        "sessions" to docs.map { memorySessionDict(it, owner, preview = true) }
    )
}

/**
 * One session as the wire carries it. On a list `content` is the first 1200 characters;
 * a `memorySession` fetch carries the whole document.
 */
private fun memorySessionDict(doc: DemoWorld.MemoryDoc, owner: String, preview: Boolean): Dict =
    mapOf(
        "name" to doc.name,
        "title" to doc.title,
        "path" to "${memoryDir(owner)}/${doc.name}.md",
        "updatedAt" to doc.updatedAt,
        "size" to doc.content.toByteArray().size,
        "content" to if (preview) doc.content.graphemePrefix(1200) else doc.content
    )

/**
 * Apply a save, returning the failure to report. The bare `"modified"` is the lost
 * compare-and-swap the phone turns into its own copy.
 */
private fun DemoServer.saveMemoryDoc(
    project: String,
    name: String,
    content: String,
    baseline: String?
): String? {
    if (!isMemorySlug(name)) {
        return "A session name may only use lowercase letters, numbers and dashes."
    }
    val owner = memoryOwner(project)
    val docs = world.memoryDocs[owner] ?: mutableListOf()
    val existing = docs.firstOrNull { it.name == name }
    // A session that isn't there yet matches only an empty baseline.
    if (baseline != null && (existing?.content ?: "") != baseline) return "modified"
    val now = nowSeconds()
    val title = memoryTitle(content, fallback = name)
    if (existing != null) {
        existing.content = content
        existing.title = title
        existing.updatedAt = now
    } else {
        docs.add(
            DemoWorld.MemoryDoc(name = name, title = title, content = content, updatedAt = now)
        )
    }
    world.memoryDocs[owner] = docs
    pushMemoryChanged(owner)
    return null
}

/**
 * The same push FSEvents produces when an agent CLI writes the folder. It carries the
 * owner, so a duplicate showing shared sessions refreshes too.
 */
private fun DemoServer.pushMemoryChanged(owner: String) {
    pushAfter(0.6) { mapOf("t" to "memory-changed", "project" to owner) }
}

private fun isMemorySlug(name: String): Boolean {
    if (name.length !in 1..64) return false
    if (name[0] !in '0'..'9' && name[0] !in 'a'..'z') return false
    return name.all { it == '-' || it in '0'..'9' || it in 'a'..'z' }
}

private fun memoryTitle(content: String, fallback: String): String {
    for (line in content.swiftLines()) {
        val trimmed = line.trimmingWhitespaces()
        if (!trimmed.startsWith("# ")) continue
        val title = trimmed.drop(2).trimmingWhitespaces()
        return title.ifEmpty { fallback }
    }
    return fallback
}
