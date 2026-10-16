package cx.lpm.mobile.core.demo

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// The Stats screen's dataset: two weeks of deterministic Claude Code and Codex sessions.

private class StatSession(
    val dayIndex: Int,
    val provider: String,
    val project: String,
    val model: String,
    val startedAt: Long,
    val lastAt: Long,
    val input: Long,
    val cacheCreation: Long,
    val cacheRead: Long,
    val output: Long,
    val reasoning: Long
)

private class TokenAgg {
    var sessions = 0
    var input = 0L
    var cacheCreation = 0L
    var cacheRead = 0L
    var output = 0L
    var reasoning = 0L

    fun add(s: StatSession) {
        sessions += 1
        input += s.input
        cacheCreation += s.cacheCreation
        cacheRead += s.cacheRead
        output += s.output
        reasoning += s.reasoning
    }

    val tokensDict: Dict
        get() = mapOf(
            "inputTokens" to input,
            "cachedInputTokens" to cacheCreation + cacheRead,
            "cacheCreationInputTokens" to cacheCreation,
            "cacheReadInputTokens" to cacheRead,
            "outputTokens" to output,
            "reasoningTokens" to reasoning,
            "totalTokens" to input + output
        )
}

private fun statFrac(a: Int, b: Int, salt: Int): Double {
    var x = (a.toLong() * 2_654_435_761L + b.toLong() * 40_503L + salt.toLong() * 69_427L).toULong()
    x = x xor (x shr 33)
    x *= 0xff51_afd7_ed55_8ccduL
    x = x xor (x shr 33)
    return (x % 10_000uL).toDouble() / 10_000
}

private fun statSessions(zone: ZoneId, now: Instant): List<StatSession> {
    val projectPool = listOf(
        "storefront",
        "storefront",
        "api-gateway",
        "storefront",
        "mobile-app",
        "storefront",
        "blog",
        "design-system"
    )
    val today = now.atZone(zone).toLocalDate()
    val nowMs = now.toEpochMilli()
    val out = mutableListOf<StatSession>()
    for (d in 0 until 14) {
        val day = today.minusDays(d.toLong())
        val dayStartMs = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val weekend = day.dayOfWeek == DayOfWeek.SUNDAY || day.dayOfWeek == DayOfWeek.SATURDAY
        val count = if (weekend) 1 + d % 2 else 3 + (d * 7) % 5
        for (s in 0 until count) {
            val f0 = statFrac(d, s, 1)
            val f1 = statFrac(d, s, 2)
            val f2 = statFrac(d, s, 3)
            val f3 = statFrac(d, s, 4)
            val project = projectPool[(d * 3 + s * 5) % projectPool.size]
            // All timestamps are unix millis, like the real scanner.
            val lastAt = if (d == 0) {
                val back = 1_400_000L + s * 7_600_000L + (f0 * 1_500_000).toLong()
                minOf(nowMs, maxOf(dayStartMs + 1_800_000, nowMs - back))
            } else {
                dayStartMs + ((9.5 + s * 1.9 + f0 * 1.3) * 3_600_000).toLong()
            }
            val startedAt = lastAt - ((900 + f1 * 5_400) * 1_000).toLong()
            out += if ((d + s) % 3 == 2) {
                val input = 320_000L + (f1 * 1_100_000).toLong()
                val output = 12_000L + (f2 * 28_000).toLong()
                StatSession(
                    dayIndex = d,
                    provider = "codex",
                    project = project,
                    model = "gpt-5.1-codex",
                    startedAt = startedAt,
                    lastAt = lastAt,
                    input = input,
                    cacheCreation = 0,
                    cacheRead = (input * (0.55 + f2 * 0.2)).toLong(),
                    output = output,
                    reasoning = (output * (0.35 + f3 * 0.25)).toLong()
                )
            } else {
                val input = 1_200_000L + (f1 * 3_600_000).toLong()
                StatSession(
                    dayIndex = d,
                    provider = "claude",
                    project = project,
                    model = if ((d + s) % 3 == 0) "claude-opus-4-5" else "claude-sonnet-4-5",
                    startedAt = startedAt,
                    lastAt = lastAt,
                    input = input,
                    cacheCreation = (input * (0.05 + f3 * 0.05)).toLong(),
                    cacheRead = (input * (0.70 + f2 * 0.16)).toLong(),
                    output = 18_000L + (f2 * 52_000).toLong(),
                    reasoning = 0
                )
            }
        }
    }
    return out
}

private fun sessionTokensDict(s: StatSession): Dict = TokenAgg().apply { add(s) }.tokensDict

private fun breakdownDicts(
    sessions: List<StatSession>,
    key: (StatSession) -> String,
    label: (String) -> String
): List<Dict> {
    val aggs = linkedMapOf<String, TokenAgg>()
    for (s in sessions) aggs.getOrPut(key(s)) { TokenAgg() }.add(s)
    return aggs.map { (k, a) ->
        mapOf("key" to k, "label" to label(k), "sessions" to a.sessions, "tokens" to a.tokensDict)
    }
}

internal fun DemoServer.statsPayload(days: Int): Dict {
    val zone = clock.zone
    val now = clock.instant()
    val nowMs = now.toEpochMilli()
    val all = statSessions(zone, now)
    val included = if (days == 0) all else all.filter { it.dayIndex < days }

    val totals = TokenAgg()
    for (s in included) totals.add(s)

    val today: LocalDate = now.atZone(zone).toLocalDate()
    val daily = (13 downTo 0).mapNotNull { d ->
        val daySessions = included.filter { it.dayIndex == d }
        if (daySessions.isEmpty()) return@mapNotNull null
        val day = today.minusDays(d.toLong())
        val claude = daySessions.filter { it.provider == "claude" }.sumOf { it.input + it.output }
        val codex = daySessions.filter { it.provider == "codex" }.sumOf { it.input + it.output }
        mapOf(
            // ISO yyyy-MM-dd, as the Mac writes it.
            "date" to day.toString(),
            "claudeTokens" to claude,
            "codexTokens" to codex,
            "totalTokens" to claude + codex
        )
    }

    val recent = included.sortedByDescending { it.lastAt }.take(12).map { s ->
        mapOf(
            "provider" to s.provider,
            "project" to s.project,
            "model" to s.model,
            "startedAt" to s.startedAt,
            "lastAt" to s.lastAt,
            "tokens" to sessionTokensDict(s)
        )
    }

    val providerLabels = mapOf("claude" to "Claude Code", "codex" to "Codex")
    return mapOf(
        "t" to "stats",
        "ok" to true,
        "stats" to mapOf(
            "generatedAt" to nowMs,
            // Echo the requested period: the phone drops a reply whose days don't match
            // the current selection.
            "days" to days,
            "sessions" to included.size,
            "totals" to totals.tokensDict,
            "providers" to breakdownDicts(included, { it.provider }) { providerLabels[it] ?: it },
            "projects" to breakdownDicts(included, { it.project }) { it },
            "models" to breakdownDicts(included, { it.model }) { it },
            "daily" to daily,
            "recentSessions" to recent,
            "sources" to listOf(
                mapOf("provider" to "claude", "files" to all.count { it.provider == "claude" }),
                mapOf("provider" to "codex", "files" to all.count { it.provider == "codex" })
            )
        )
    )
}
