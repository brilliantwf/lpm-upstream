package cx.lpm.mobile.core.demo

/**
 * Demo handlers for the agent-usage Stats screen and the paged message-History screen
 * (query/paging, favorites, folders, drafts, delete).
 */
internal fun DemoServer.registerStatsHandlers() {
    seedHistoryFixtures()

    // Demo Mode has no Mac to synthesize on. Answer with a clear error rather than
    // leaving the reader spinning forever.
    register("ttsSpeak") { o ->
        val reqId = o.string("reqId") ?: ""
        pushAfter(0.4) {
            mapOf(
                "t" to "ttsSpeak",
                "reqId" to reqId,
                "ok" to false,
                "error" to "Reading aloud on the Mac isn't available in the demo. " +
                    "Switch the engine to This iPhone."
            )
        }
    }

    register("stats") { o ->
        val days = o["days"].numberValue()?.toInt() ?: 30
        pushAfter(0.6) { statsPayload(days) }
    }

    register("historyQuery") { o ->
        val project = o.string("project")?.takeIf { it.isNotEmpty() }
        val search = o.string("search")?.takeIf { it.isNotEmpty() }?.lowercase()
        val favoritesOnly = o.bool("favoritesOnly") ?: false
        val folder = o.string("folder")?.takeIf { it.isNotEmpty() }
        var items: List<DemoWorld.HistoryMessage> = world.historyMessages
        if (project != null) items = items.filter { it.project == project }
        if (favoritesOnly) {
            items = items.filter { it.favorite }
        } else if (folder != null) {
            items = items.filter { it.folder == folder }
        }
        if (search != null) items = items.filter { it.text.lowercase().contains(search) }
        items = items.sortedWith(
            compareByDescending<DemoWorld.HistoryMessage> { it.at }.thenByDescending { it.seq }
        )
        val before = o.dict("before")
        val at = before?.get("at").numberValue()
        val seq = before?.get("seq").numberValue()
        if (at != null && seq != null) {
            items = items.filter { it.at < at || (it.at == at && it.seq < seq) }
        }
        val page = items.take(60)
        push(
            mapOf(
                "t" to "historyQuery",
                "items" to page.map(::historyItemDict),
                "hasMore" to (items.size > page.size)
            )
        )
    }

    register("historySaveDraft") { o ->
        val text = o.string("message") ?: o.dict("message")?.string("text")
        val nowMs = nowMillis()
        val seq = (world.historyMessages.maxOfOrNull { it.seq } ?: 0) + 1
        world.historyMessages.add(
            DemoWorld.HistoryMessage(
                id = "hm-$seq",
                text = text ?: "",
                timestamp = nowMs,
                kind = "draft",
                project = o.string("project") ?: "",
                at = nowMs,
                seq = seq
            )
        )
        push(mapOf("t" to "historySaveDraft", "ok" to true))
    }

    register("historyToggleFavorite") { o ->
        val id = o.string("id") ?: return@register
        val message = world.historyMessages.firstOrNull { it.id == id }
        if (message == null) {
            push(
                mapOf(
                    "t" to "historyToggleFavorite",
                    "id" to id,
                    "ok" to false,
                    "error" to "Message not found."
                )
            )
            return@register
        }
        message.favorite = !message.favorite
        push(
            mapOf(
                "t" to "historyToggleFavorite",
                "id" to id,
                "ok" to true,
                "favorite" to message.favorite
            )
        )
    }

    register("historySetFolder") { o ->
        val id = o.string("id") ?: return@register
        val message = world.historyMessages.firstOrNull { it.id == id }
        if (message == null) {
            push(mapOf("t" to "historySetFolder", "ok" to false, "error" to "Message not found."))
            return@register
        }
        message.folder = o.string("folder")?.takeIf { it.isNotEmpty() }
        push(mapOf("t" to "historySetFolder", "ok" to true))
    }

    register("historyDelete") { o ->
        val id = o.string("id") ?: return@register
        world.historyMessages.removeAll { it.id == id }
        push(mapOf("t" to "historyDelete", "ok" to true))
    }

    registerHistoryFolderHandlers()
}

private fun DemoServer.registerHistoryFolderHandlers() {
    register("historyFolders") {
        val folders = world.historyFolders.sortedWith(
            compareBy(localizedCaseInsensitive) {
                it.name
            }
        )
        push(mapOf("t" to "historyFolders", "folders" to folders.map { historyFolderDict(it) }))
    }

    register("historyCreateFolder") { o ->
        val name = (o.string("name") ?: "").trimmingWhitespacesAndNewlines()
        if (name.isEmpty()) {
            push(
                mapOf(
                    "t" to "historyCreateFolder",
                    "ok" to false,
                    "error" to "Folder name can't be empty."
                )
            )
            return@register
        }
        val existing = world.historyFolders.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (existing != null) {
            push(
                mapOf(
                    "t" to "historyCreateFolder",
                    "ok" to true,
                    "folder" to historyFolderDict(existing)
                )
            )
            return@register
        }
        val taken = world.historyFolders.mapNotNull { it.id.drop("folder-".length).toIntOrNull() }
        val next = (taken.maxOrNull() ?: 0) + 1
        val folder = DemoWorld.HistoryFolder(id = "folder-$next", name = name)
        world.historyFolders.add(folder)
        push(
            mapOf("t" to "historyCreateFolder", "ok" to true, "folder" to historyFolderDict(folder))
        )
    }

    register("historyDeleteFolder") { o ->
        val byId = o.string("id")
        val byName = o.string("name")
        val folder = world.historyFolders.firstOrNull {
            (byId != null && it.id == byId) || (byName != null && it.name == byName)
        }
        if (folder != null) {
            for (m in world.historyMessages) if (m.folder == folder.id) m.folder = null
            world.historyFolders.removeAll { it.id == folder.id }
        }
        push(mapOf("t" to "historyDeleteFolder", "ok" to true))
    }
}

// history fixtures + builders

private class HistorySeed(
    val minutesAgo: Int,
    val text: String,
    val project: String,
    val kind: String = "sent",
    val favorite: Boolean = false,
    val folder: String? = null
)

private fun DemoServer.seedHistoryFixtures() {
    val nowMs = nowMillis()
    val checkout = "folder-1"
    world.historyFolders = mutableListOf(DemoWorld.HistoryFolder(id = checkout, name = "Checkout"))

    val newestFirst = mutableListOf(
        HistorySeed(
            18,
            "Investigate the flaky checkout e2e — start with the payment mock timeouts",
            "storefront",
            "draft"
        ),
        HistorySeed(
            42,
            "Fix the checkout button spacing on mobile",
            "storefront",
            folder = checkout
        ),
        HistorySeed(
            95,
            "Handle declined cards gracefully in the checkout flow",
            "storefront",
            favorite = true,
            folder = checkout
        ),
        HistorySeed(
            160,
            "Add rate limiting middleware to the gateway",
            "api-gateway",
            favorite = true
        ),
        HistorySeed(230, "Write unit tests for lib/payments.ts", "storefront", folder = checkout),
        HistorySeed(310, "Fix the flicker when switching tabs on Android", "mobile-app"),
        HistorySeed(
            420,
            "Refactor CheckoutForm to use react-hook-form",
            "storefront",
            favorite = true,
            folder = checkout
        ),
        HistorySeed(560, "Draft a post about the Astro migration", "blog"),
        HistorySeed(700, "Fix Button focus ring in dark mode", "design-system"),
        HistorySeed(840, "Add loading skeletons to the product grid", "storefront"),
        HistorySeed(1_000, "Return 429 with Retry-After when the limiter trips", "api-gateway"),
        HistorySeed(
            1_200,
            "Plan the Q3 component audit — list components missing dark mode",
            "design-system",
            "draft"
        ),
        HistorySeed(1_400, "Why is the cart badge not updating after adding an item?", "storefront")
    )

    for (pass in 0 until 2) {
        historyFiller.forEachIndexed { i, (text, project) ->
            val age = 1_600 + (pass * historyFiller.size + i) * 710
            newestFirst.add(HistorySeed(age, text, project))
        }
    }

    world.historyMessages = newestFirst.reversed().mapIndexedTo(mutableListOf()) { i, e ->
        val at = nowMs - e.minutesAgo * 60_000L
        DemoWorld.HistoryMessage(
            id = "hm-${i + 1}",
            text = e.text,
            timestamp = at,
            favorite = e.favorite,
            folder = e.folder,
            kind = e.kind,
            project = e.project,
            at = at,
            seq = i + 1
        )
    }
}

private val historyFiller = listOf(
    "Add empty state to the order history page" to "storefront",
    "Optimize product images with next/image" to "storefront",
    "Add a promo code field to the cart summary" to "storefront",
    "Debounce the header search input" to "storefront",
    "Migrate the product page to server components" to "storefront",
    "Add Apple Pay to the payment options" to "storefront",
    "Tighten Lighthouse scores on the landing page" to "storefront",
    "Fix the hydration warning on the cart drawer" to "storefront",
    "Add structured logging with request IDs" to "api-gateway",
    "Write a health check that verifies upstream connectivity" to "api-gateway",
    "Profile the proxy hot path and cut allocations" to "api-gateway",
    "Add graceful shutdown on SIGTERM" to "api-gateway",
    "Cache upstream auth lookups for a minute" to "api-gateway",
    "Add pull to refresh on the feed screen" to "mobile-app",
    "Persist auth tokens in the secure store" to "mobile-app",
    "Profile app startup and trim the bundle" to "mobile-app",
    "Fix the keyboard avoiding view on the login screen" to "mobile-app",
    "Add RSS feed generation" to "blog",
    "Fix code block overflow on small screens" to "blog",
    "Add reading time estimates to post headers" to "blog",
    "Set up OG image generation for posts" to "blog",
    "Add a Tooltip component with keyboard support" to "design-system",
    "Document the color tokens in Storybook" to "design-system",
    "Add size variants to the TextField component" to "design-system",
    "Write visual regression tests for Button states" to "design-system"
)

private fun historyItemDict(m: DemoWorld.HistoryMessage): Dict {
    val d = mutableMapOf<String, Any?>(
        "id" to m.id,
        "text" to m.text,
        "images" to emptyMap<String, String>(),
        "timestamp" to m.timestamp,
        "favorite" to m.favorite,
        "kind" to m.kind,
        "project" to m.project,
        "at" to m.at,
        "seq" to m.seq
    )
    m.folder?.let { d["folder"] = it }
    return d
}

private fun DemoServer.historyFolderDict(f: DemoWorld.HistoryFolder): Dict {
    val count = world.historyMessages.count { it.folder == f.id }
    return mapOf("id" to f.id, "name" to f.name, "count" to count)
}
