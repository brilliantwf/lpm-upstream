package cx.lpm.mobile.core.demo

import java.time.format.DateTimeFormatter
import java.util.Locale

// Seed session logs for Session Memory, in the shape the lpm-memory skill writes.

private class MemoryEntry(val minutesAgo: Int, val agent: String, val lines: List<String>)

internal fun DemoServer.seedMemoryFixtures() {
    world.memoryDocs["storefront"] = mutableListOf(
        memoryDoc(
            name = "checkout-redesign",
            title = "Checkout redesign",
            goal = "Rebuild checkout as a two-column flow and move card handling into a payments " +
                "module the API can share.",
            state = "The two-column layout and the payment method picker are on " +
                "`feat/checkout-redesign`. Card and Apple Pay both go through `lib/payments.ts`, " +
                "and a declined card now shows an inline message instead of a toast. Totals are " +
                "memoized. The end-to-end run still flakes around the payment mock — that is the " +
                "only thing between this branch and review.",
            entries = listOf(
                MemoryEntry(
                    5_760,
                    "claude",
                    listOf(
                        "- Done: Split the checkout page into a form column and an order summary column",
                        "- Decided: Wrapped the existing form instead of rewriting it — its validation rules are the only ones covered by tests",
                        "- Next: Pull card handling out of the form"
                    )
                ),
                MemoryEntry(
                    3_100,
                    "codex",
                    listOf(
                        "- Done: Added `lib/payments.ts` with `createPaymentIntent` and a `PaymentMethod` union",
                        "- Learned: A declined card comes back as 402 with an empty body, so the message shown to the shopper has to come from the client",
                        "- Next: Wire the picker into the form"
                    )
                ),
                MemoryEntry(
                    1_450,
                    "claude",
                    listOf(
                        "- Done: Payment method picker wired in; a declined card shows an inline error and keeps the form filled",
                        "- Decided: Create the payment intent before submit so a decline never loses what was typed",
                        "- Open: The order summary recomputes totals on every keystroke",
                        "- Next: Memoize the totals"
                    )
                ),
                MemoryEntry(
                    95,
                    "claude",
                    listOf(
                        "- Done: Memoized the totals; the summary no longer recomputes while typing",
                        "- Learned: The end-to-end payment mock resolves after the assertion on a cold run, roughly one run in five",
                        "- Next: Warm the mock in the test setup, then run the suite three times before opening the pull request"
                    )
                )
            )
        ),
        memoryDoc(
            name = "payments-module",
            title = "Payments module",
            goal = "Give the storefront and the gateway one place to create and confirm payments.",
            state = "`createPaymentIntent` is in and used by checkout. Refunds are still a " +
                "gateway-only path; the shared client stops at confirmation.",
            entries = listOf(
                MemoryEntry(
                    7_300,
                    "codex",
                    listOf(
                        "- Done: Sketched the module boundary — intent creation and confirmation shared, refunds stay server side",
                        "- Decided: Kept the amount in minor units end to end to avoid rounding on the way through"
                    )
                ),
                MemoryEntry(
                    1_580,
                    "codex",
                    listOf(
                        "- Done: Confirmation path landed with a typed error for declines",
                        "- Open: Retries after a network drop can create a second intent — needs an idempotency key",
                        "- Next: Thread an idempotency key through from the form"
                    )
                )
            )
        ),
        memoryDoc(
            name = "cart-badge-bug",
            title = "Cart badge not updating",
            goal = "The header badge misses the first item added from a product page.",
            state = "Traced to the badge reading a snapshot taken before the cart store " +
                "hydrates. A fix is drafted but not committed — reproduce once more on a cold " +
                "load first.",
            entries = listOf(
                MemoryEntry(
                    7_000,
                    "claude",
                    listOf(
                        "- Done: Reproduced on a cold load; a warm load is always correct",
                        "- Learned: The badge subscribes after hydration finishes, so the first change is missed",
                        "- Next: Subscribe before hydration and seed from the hydrated value"
                    )
                )
            )
        )
    )
    world.memoryDocs["api-gateway"] = mutableListOf(
        memoryDoc(
            name = "rate-limiting",
            title = "Rate limiting middleware",
            goal = "Protect the upstream services with a per-key limiter that fails open.",
            state = "A token bucket per API key is in front of the proxy handler. Limits are " +
                "read from config at boot; reloading them without a restart is still open.",
            entries = listOf(
                MemoryEntry(
                    4_320,
                    "codex",
                    listOf(
                        "- Done: Token bucket per key, 429 with `Retry-After` when it trips",
                        "- Decided: Fail open if the limiter's own store is unreachable — a limiter outage must not become an API outage"
                    )
                ),
                MemoryEntry(
                    2_800,
                    "codex",
                    listOf(
                        "- Done: Added counters so the limiter shows up in the metrics endpoint",
                        "- Open: Limits only load at boot",
                        "- Next: Watch the config file and swap the buckets in place"
                    )
                )
            )
        ),
        memoryDoc(
            name = "graceful-shutdown",
            title = "Graceful shutdown",
            goal = "Drain in-flight requests on SIGTERM instead of dropping them.",
            state = "Draining works; the shutdown deadline is hard-coded at 20s and should be " +
                "configurable.",
            entries = listOf(
                MemoryEntry(
                    12_900,
                    "claude",
                    listOf(
                        "- Done: Stop accepting connections on SIGTERM, wait for in-flight requests, then exit",
                        "- Learned: Long-polling clients hold the drain open — the deadline has to be short enough to matter"
                    )
                )
            )
        )
    )
    world.memoryDocs["mobile-app"] = mutableListOf(
        memoryDoc(
            name = "startup-time",
            title = "App startup time",
            goal = "Get a cold start under two seconds on a mid-range device.",
            state = "Cold start is down from 3.4s to 2.3s. The remaining cost is the font and " +
                "icon bundle loaded before the first screen paints.",
            entries = listOf(
                MemoryEntry(
                    9_800,
                    "claude",
                    listOf(
                        "- Done: Profiled a cold start and found two thirds of it before the first frame",
                        "- Decided: Defer analytics and remote config until after the first screen"
                    )
                ),
                MemoryEntry(
                    8_640,
                    "claude",
                    listOf(
                        "- Done: Deferred both; cold start now 2.3s",
                        "- Next: Subset the font and lazy-load the icon set"
                    )
                )
            )
        )
    )
}

/**
 * Build one session log: a goal, the current state, and an append-only timeline. The
 * document's timestamp is its newest entry.
 */
private fun DemoServer.memoryDoc(
    name: String,
    title: String,
    goal: String,
    state: String,
    entries: List<MemoryEntry>
): DemoWorld.MemoryDoc {
    var content = "# $title\n\n## Goal\n$goal\n\n## Current state\n$state\n\n## Timeline"
    for (entry in entries) {
        content += "\n\n### ${memoryStamp(entry.minutesAgo)} — ${entry.agent}\n"
        content += entry.lines.joinToString("\n")
    }
    val newest = entries.minOfOrNull { it.minutesAgo } ?: 0
    return DemoWorld.MemoryDoc(
        name = name,
        title = title,
        content = content + "\n",
        updatedAt = nowSeconds() - newest * 60
    )
}

private val memoryStampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

private fun DemoServer.memoryStamp(minutesAgo: Int): String =
    memoryStampFormat.format(clock.instant().minusSeconds(minutesAgo * 60L).atZone(clock.zone))
