package cx.lpm.mobile.core.demo

// Shared plumbing for the terminal engine: screen buffers, output frames, and lookups.

/**
 * A status entry the way an agent's hooks report one: the key carries the provider
 * (`claude_code_<session>`), `paneID` the terminal it came from.
 */
internal fun DemoServer.statusEntry(terminal: String, value: String, priority: Int): Dict = mapOf(
    "key" to "claude_code_$terminal",
    "value" to value,
    "priority" to priority,
    "timestamp" to nowMillis(),
    "paneID" to terminal
)

internal fun fileExt(mime: String): String = when (mime) {
    "image/jpeg" -> "jpg"
    "image/png" -> "png"
    "image/gif" -> "gif"
    "image/heic" -> "heic"
    "application/pdf" -> "pdf"
    else -> "bin"
}

/** How many Characters the escape sequence starting at [i] spans (CSI, OSC, or one). */
internal fun skipEscape(chars: List<String>, i: Int): Int {
    if (i + 1 >= chars.size) return 1
    if (chars[i + 1] == "[") {
        var j = i + 2
        while (j < chars.size) {
            if (chars[j].codePointAt(0) in 0x40..0x7E) return j - i + 1
            j += 1
        }
        return chars.size - i
    }
    if (chars[i + 1] == "]") {
        var j = i + 2
        while (j < chars.size && chars[j] != "\u0007") j += 1
        return minOf(j + 1, chars.size) - i
    }
    return 2
}

internal fun DemoServer.emit(id: String, text: String) {
    appendBuffer(id, text)
    push(mapOf("t" to "o", "id" to id, "d" to text))
}

internal fun DemoServer.emitAfter(delay: Double, id: String, text: String) {
    pushAfter(delay) {
        if (demoTerminal(id) == null) return@pushAfter null
        appendBuffer(id, text)
        mapOf("t" to "o", "id" to id, "d" to text)
    }
}

internal fun DemoServer.appendBuffer(id: String, text: String) {
    updateTerminal(id) { t ->
        t.buffer += text
        // The length check only skips counting Characters when it can't exceed the cap.
        if (t.buffer.length > 24_000 && t.buffer.graphemeCount() > 24_000) {
            var trimmed = t.buffer.graphemeSuffix(20_000)
            val nl = trimmed.indexOf("\r\n")
            if (nl >= 0) trimmed = trimmed.substring(nl + 2)
            t.buffer = trimmed
        }
    }
}

internal fun DemoServer.setBuffer(id: String, text: String) {
    updateTerminal(id) { it.buffer = text }
}

internal fun DemoServer.demoTerminal(id: String): DemoWorld.Terminal? =
    world.terminals.values.firstNotNullOfOrNull { list -> list.firstOrNull { it.id == id } }

internal fun DemoServer.updateTerminal(id: String, mutate: (DemoWorld.Terminal) -> Unit) {
    demoTerminal(id)?.let(mutate)
}
