package cx.lpm.mobile.core.demo

import cx.lpm.mobile.core.demo.DemoWorld.Job
import cx.lpm.mobile.core.demo.DemoWorld.JobRun
import java.time.Instant
import java.time.LocalTime

// The seed: automations, the storefront's project config, and message history.

internal fun DemoWorld.seedJobs(nowS: Long) {
    val now = Instant.ofEpochSecond(nowS).atZone(clock.zone)
    val anchor = now.with(LocalTime.of(3, 0))
    val lastNight = if (!anchor.isAfter(now)) anchor else anchor.minusDays(1)
    val t0 = lastNight.toEpochSecond()

    val passOutput = listOf(
        " ✓ lib/payments.test.ts (12 tests) 267ms",
        " ✓ components/CheckoutForm.test.tsx (8 tests) 412ms",
        " ✓ app/checkout/page.test.tsx (5 tests) 638ms",
        "",
        " Test Files  3 passed (3)",
        "      Tests  25 passed (25)",
        "   Duration  6.9s"
    ).joinToString("\n")
    val failOutput = listOf(
        " ✓ lib/payments.test.ts (12 tests) 271ms",
        " ✗ components/CheckoutForm.test.tsx (8 tests | 1 failed) 460ms",
        "   → expected discount total to equal 41.98, got 45.98",
        " ✓ app/checkout/page.test.tsx (5 tests) 655ms",
        "",
        " Test Files  1 failed | 2 passed (3)",
        "      Tests  1 failed | 24 passed (25)",
        "   Duration  7.2s"
    ).joinToString("\n")
    val rerunOutput = listOf(
        " ✓ lib/payments.test.ts (12 tests) 259ms",
        " ✓ components/CheckoutForm.test.tsx (8 tests) 405ms",
        " ✓ app/checkout/page.test.tsx (5 tests) 641ms",
        "",
        " Test Files  3 passed (3)",
        "      Tests  25 passed (25)",
        "   Duration  6.7s"
    ).joinToString("\n")
    val weeklyPrompt = "Check the project's dependencies for outdated packages and " +
        "summarize which updates are safe to apply."

    jobs = mutableListOf(
        Job(
            id = "nightly-tests",
            project = "storefront",
            label = "Nightly tests",
            emoji = "🌙",
            enabled = true,
            runKind = "cmd",
            summary = "npm test",
            scheduleMode = "calendar",
            atMinutes = 180,
            lastRunAt = t0,
            lastResult = "completed",
            nextFireAt = t0 + 86400,
            history = listOf(
                JobRun(
                    at = t0 - 2 * 86400,
                    result = "error",
                    output = failOutput,
                    durationSecs = 61
                ),
                JobRun(
                    at = t0 - 2 * 86400 + 1560,
                    result = "completed",
                    output = rerunOutput,
                    durationSecs = 70
                ),
                JobRun(
                    at = t0 - 86400,
                    result = "completed",
                    output = passOutput,
                    durationSecs = 69
                ),
                JobRun(at = t0, result = "completed", output = passOutput, durationSecs = 72)
            ),
            body = mapOf(
                "label" to "Nightly tests",
                "emoji" to "🌙",
                "schedule" to mapOf("at" to "03:00"),
                "run" to mapOf("cmd" to "npm test")
            ),
            // Read up to the night before, so the demo opens with last night's run
            // still waiting to be looked at.
            seenAt = t0 - 1
        ),
        Job(
            id = "weekly-deps",
            project = "blog",
            label = "Weekly dependency check",
            emoji = "📦",
            enabled = false,
            runKind = "prompt",
            summary = weeklyPrompt,
            scheduleMode = "calendar",
            atMinutes = 360,
            days = listOf("mon"),
            lastResult = "",
            agent = "claude",
            body = mapOf(
                "label" to "Weekly dependency check",
                "emoji" to "📦",
                "schedule" to mapOf("at" to "06:00", "days" to listOf("mon")),
                "run" to mapOf("prompt" to weeklyPrompt, "agent" to "claude")
            )
        )
    )
}

internal fun DemoWorld.seedConfig() {
    configText["storefront\nproject"] = """
        services:
          web:
            cmd: npm run dev
            port: 3000
          api:
            cmd: npm run api
            port: 4000
          worker:
            cmd: npm run worker
        actions:
          test:
            cmd: npm test
            display: checks
          lint:
            cmd: npm run lint
            display: checks
          fix-tests:
            label: Fix tests
            cmd: claude "Fix the failing tests"
            type: terminal
            color: claude
            display: agents
            layer: claude
          review:
            label: Review
            cmd: claude "Review my changes"
            type: terminal
            color: claude
            display: agents
            layer: claude
          codex-review:
            label: Review
            cmd: codex "Review my changes"
            type: terminal
            color: cyan
            display: agents
            layer: codex
        zones:
          checks:
            rows: 1
            label: Checks
            position: 1
          agents:
            rows: 2
            label: Agents
            position: 2
            layers:
              claude:
                label: Claude
                position: 1
              codex:
                label: Codex
                position: 2
    """.trimIndent()
}

internal fun DemoWorld.seedHistory(nowMs: Long) {
    historyMessages = mutableListOf(
        DemoWorld.HistoryMessage(
            id = "h1",
            text = "Refactor the checkout form validation",
            timestamp = nowMs - 3_600_000,
            favorite = true,
            project = "storefront",
            at = nowMs - 3_600_000,
            seq = 3
        ),
        DemoWorld.HistoryMessage(
            id = "h2",
            text = "Add unit tests for the payments module",
            timestamp = nowMs - 7_200_000,
            project = "storefront",
            at = nowMs - 7_200_000,
            seq = 2
        ),
        DemoWorld.HistoryMessage(
            id = "h3",
            text = "Update the README with setup steps",
            timestamp = nowMs - 90_000_000,
            project = "blog",
            at = nowMs - 90_000_000,
            seq = 1
        )
    )
}
