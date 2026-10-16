package cx.lpm.mobile.core.demo

import java.util.UUID

// What a simulated automation run prints, how long it takes, and how it ends.

internal class RunScript(
    val steps: List<Pair<Double, String>>,
    val duration: Double,
    val result: String,
    val output: String,
    val session: String,
    val costUsd: Double?
)

internal fun DemoServer.runScript(j: DemoWorld.Job, question: String?): RunScript {
    if (question != null) return followupScript(j)
    return when (j.id) {
        "nightly-tests" -> nightlyTestsScript()
        "weekly-deps" -> weeklyDepsScript()
        else -> if (j.runKind == "prompt") genericPromptScript(j) else genericCmdScript(j)
    }
}

private fun nightlyTestsScript(): RunScript {
    val summary = "\n Test Files  3 passed (3)\n      Tests  25 passed (25)\n   Duration  6.8s\n"
    return RunScript(
        steps = listOf(
            0.4 to "\n> storefront@0.4.2 test\n> vitest run\n\n",
            1.6 to " RUN  v3.1.4 /Users/demo/dev/storefront\n\n",
            3.0 to " ✓ lib/payments.test.ts (12 tests) 262ms\n",
            4.4 to " ✓ components/CheckoutForm.test.tsx (8 tests) 418ms\n",
            5.8 to " ✓ app/checkout/page.test.tsx (5 tests) 630ms\n",
            7.2 to summary
        ),
        duration = 8.2,
        result = "completed",
        output = " ✓ lib/payments.test.ts (12 tests) 262ms\n" +
            " ✓ components/CheckoutForm.test.tsx (8 tests) 418ms\n" +
            " ✓ app/checkout/page.test.tsx (5 tests) 630ms\n" + summary,
        session = "",
        costUsd = null
    )
}

private fun weeklyDepsScript(): RunScript = RunScript(
    steps = listOf(
        0.5 to "Reading package.json…\n",
        2.0 to "Checking the npm registry for newer versions…\n",
        3.5 to "astro 4.8.3 → 4.9.1 (minor)\n",
        4.8 to "@astrojs/mdx 3.0.0 → 3.0.1 (patch)\n",
        6.0 to "sharp 0.33.3 → 0.33.4 (patch)\n",
        7.2 to "Reviewing release notes for breaking changes…\n"
    ),
    duration = 8.4,
    result = "completed",
    output = """
        Checked 24 dependencies in package.json. Three are outdated:

        • astro 4.8.3 → 4.9.1 (minor)
        • @astrojs/mdx 3.0.0 → 3.0.1 (patch)
        • sharp 0.33.3 → 0.33.4 (patch)

        All three look safe — no breaking changes in the release notes. Run npm update when ready.
    """.trimIndent(),
    session = newSession(),
    costUsd = 0.14
)

private fun genericPromptScript(j: DemoWorld.Job): RunScript {
    val prompt = j.body.dict("run")?.string("prompt") ?: ""
    return RunScript(
        steps = listOf(
            0.6 to "Starting agent…\n",
            2.2 to "Reading the project…\n",
            4.0 to "Working through the task…\n",
            6.2 to "Wrapping up…\n"
        ),
        duration = 7.8,
        result = "completed",
        output = if (prompt.isEmpty()) {
            "Finished the task. Everything checked out — no further action needed."
        } else {
            "Finished: $prompt\n\nEverything checked out — no further action needed."
        },
        session = newSession(),
        costUsd = 0.09
    )
}

private fun DemoServer.genericCmdScript(j: DemoWorld.Job): RunScript {
    val run = j.body.dict("run")
    val actionName = run?.string("action")
    val cmd = run?.string("cmd")
        ?: actionName?.let { name ->
            val actions = world.project(j.project)?.actions ?: emptyList()
            findAction(name, actions)?.string("cmd") ?: name
        }
        ?: "npm test"
    return RunScript(
        steps = listOf(
            0.4 to "$ $cmd\n",
            2.0 to "Running…\n",
            5.5 to "Finished without errors.\n"
        ),
        duration = 7.0,
        result = "completed",
        output = "$ $cmd\nRunning…\nFinished without errors.",
        session = "",
        costUsd = null
    )
}

private fun followupScript(j: DemoWorld.Job): RunScript {
    val reply = if (j.id == "weekly-deps") {
        "I took another look. Nothing risky in those updates — astro 4.9.1 only tightens a " +
            "few types, and both patches are pure bug fixes. Safe to update whenever you like."
    } else {
        "I took another look at the last run and double-checked the details you asked about. " +
            "Everything holds up — no further changes needed."
    }
    return RunScript(
        steps = listOf(
            0.5 to "Resuming session…\n",
            1.8 to "Re-reading the last run…\n",
            3.5 to "Working on your reply…\n"
        ),
        duration = 6.0,
        result = "completed",
        output = reply,
        session = newSession(),
        costUsd = 0.07
    )
}

private fun newSession(): String = "demo-sess-" + UUID.randomUUID().toString().take(8)
