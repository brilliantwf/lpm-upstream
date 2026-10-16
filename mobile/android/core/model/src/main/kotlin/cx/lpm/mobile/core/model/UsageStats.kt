package cx.lpm.mobile.core.model

import kotlinx.serialization.json.JsonObject

// Wire mirror of the desktop `AgentUsageStats` (src-tauri/agent_usage.rs, camelCase
// JSON). Token counts can reach the billions, so they're read as 64-bit, and every
// count is read like NSNumber's integer value: any number, truncated.

private fun JsonObject.stat(key: String): Long = numberLong(key) ?: 0

private fun JsonObject.statInt(key: String): Int = stat(key).toInt()

private fun JsonObject.tokens(key: String): UsageTokens =
    UsageTokens.parse(obj(key) ?: JsonObject(emptyMap()))

/**
 * One bucket of token counts. `inputTokens` ALREADY includes the cached input
 * (cacheCreation + cacheRead); `cacheCreation1hInputTokens ⊆ cacheCreationInputTokens`;
 * `reasoningTokens ⊆ outputTokens`; `totalTokens = inputTokens + outputTokens`.
 */
data class UsageTokens(
    val inputTokens: Long,
    val cachedInputTokens: Long,
    val cacheCreationInputTokens: Long,
    val cacheCreation1hInputTokens: Long,
    val cacheReadInputTokens: Long,
    val outputTokens: Long,
    val reasoningTokens: Long,
    val totalTokens: Long
) {
    companion object {
        fun parse(o: JsonObject): UsageTokens = UsageTokens(
            inputTokens = o.stat("inputTokens"),
            cachedInputTokens = o.stat("cachedInputTokens"),
            cacheCreationInputTokens = o.stat("cacheCreationInputTokens"),
            cacheCreation1hInputTokens = o.stat("cacheCreation1hInputTokens"),
            cacheReadInputTokens = o.stat("cacheReadInputTokens"),
            outputTokens = o.stat("outputTokens"),
            reasoningTokens = o.stat("reasoningTokens"),
            totalTokens = o.stat("totalTokens")
        )
    }
}

/**
 * Tokens aggregated under one key — a provider, a project, or a model. Model rows
 * carry their provider, and fast-mode usage arrives as its own row with `fast` set.
 */
data class UsageBreakdown(
    val key: String,
    val label: String,
    val sessions: Int,
    val tokens: UsageTokens,
    val provider: String?,
    val fast: Boolean
) {
    val id: String get() = if (fast) "$key#fast" else key

    companion object {
        fun parse(o: JsonObject): UsageBreakdown = UsageBreakdown(
            key = o.string("key").orEmpty(),
            label = o.string("label").orEmpty(),
            sessions = o.statInt("sessions"),
            tokens = o.tokens("tokens"),
            provider = o.string("provider"),
            fast = o.bool("fast") ?: false
        )
    }
}

/** One day's token totals, split by provider, for the activity chart. */
data class UsageDaily(
    // "YYYY-MM-DD"
    val date: String,
    val claudeTokens: Long,
    val codexTokens: Long,
    val totalTokens: Long
) {
    val id: String get() = date

    companion object {
        fun parse(o: JsonObject): UsageDaily = UsageDaily(
            date = o.string("date").orEmpty(),
            claudeTokens = o.stat("claudeTokens"),
            codexTokens = o.stat("codexTokens"),
            totalTokens = o.stat("totalTokens")
        )
    }
}

/** One recent agent session (`startedAt`/`lastAt` are unix **milliseconds**). */
data class UsageSession(
    val provider: String,
    val project: String,
    val model: String,
    val startedAt: Long,
    val lastAt: Long,
    val tokens: UsageTokens
) {
    val id: String get() = provider + "\n" + project + "\n" + model + "\n" + startedAt

    companion object {
        fun parse(o: JsonObject): UsageSession = UsageSession(
            provider = o.string("provider").orEmpty(),
            project = o.string("project").orEmpty(),
            model = o.string("model").orEmpty(),
            startedAt = o.stat("startedAt"),
            lastAt = o.stat("lastAt"),
            tokens = o.tokens("tokens")
        )
    }
}

/** How many history files each provider contributed (for the footer note). */
data class UsageSource(val provider: String, val files: Int) {
    val id: String get() = provider

    companion object {
        fun parse(o: JsonObject): UsageSource = UsageSource(
            provider = o.string("provider").orEmpty(),
            files = o.statInt("files")
        )
    }
}

/**
 * The full stats snapshot for a period. `models` drives the cost estimate;
 * `providers`/`projects` the breakdowns; `daily` the chart.
 */
data class AgentStats(
    val generatedAt: Long,
    val days: Int,
    val sessions: Int,
    val totals: UsageTokens,
    val providers: List<UsageBreakdown>,
    val projects: List<UsageBreakdown>,
    val models: List<UsageBreakdown>,
    val daily: List<UsageDaily>,
    val recentSessions: List<UsageSession>,
    val sources: List<UsageSource>
) {
    companion object {
        fun parse(o: JsonObject): AgentStats = AgentStats(
            generatedAt = o.stat("generatedAt"),
            days = o.statInt("days"),
            sessions = o.statInt("sessions"),
            totals = o.tokens("totals"),
            providers = o.list("providers", UsageBreakdown::parse),
            projects = o.list("projects", UsageBreakdown::parse),
            models = o.list("models", UsageBreakdown::parse),
            daily = o.list("daily", UsageDaily::parse),
            recentSessions = o.list("recentSessions", UsageSession::parse),
            sources = o.list("sources", UsageSource::parse)
        )
    }
}
