package cx.lpm.mobile.feature.memory.format

/**
 * The first line under "## Goal" — the document's own one-line answer to what the
 * workstream is about.
 */
fun memoryGoalLine(content: String): String {
    val lines = content.split("\n")
    val start = lines.indexOfFirst { it.trim(' ', '\t').lowercase().startsWith("## goal") }
    if (start < 0) return ""
    for (line in lines.drop(start + 1)) {
        val trimmed = line.trim(' ', '\t')
        if (trimmed.startsWith("##")) break
        if (trimmed.isNotEmpty()) return trimmed
    }
    return ""
}

/**
 * The agent named by the newest timeline entry ("### <date> — <agent>"): who
 * remembered last, which is the freshness signal a hand-off decision needs.
 */
fun memoryLastAgent(content: String): String {
    var agent = ""
    for (line in content.split("\n")) {
        val trimmed = line.trim(' ', '\t')
        if (!trimmed.startsWith("### ")) continue
        val dash = trimmed.lastIndexOf('—')
        if (dash < 0) continue
        val tail = trimmed.substring(dash + 1).trim(' ', '\t')
        if (tail.isNotEmpty() && ' ' !in tail) agent = tail
    }
    return agent
}

/**
 * What the composer writes for the terminal's agent: `/lpm-memory <id>` runs the
 * skill in Claude Code; every other agent gets the skill-mention form `$lpm-memory
 * <id>`. The bare command means "remember this conversation" and is complete on its
 * own, so it keeps no trailing space.
 */
fun memoryInvocation(cli: String, session: String): String {
    val command = if (cli == "claude") "/lpm-memory" else "\$lpm-memory"
    return if (session.isEmpty()) command else "$command $session "
}
