package cx.lpm.mobile.core.demo

import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoNotesMemoryTest {
    private fun DemoHarness.page(beforeId: String = "", seconds: Double = 0.35): List<Dict> =
        exchange(
            seconds,
            "notesMessages",
            "project" to "storefront",
            "chatId" to "chat-1",
            "limit" to 50,
            "beforeId" to beforeId
        ).single("notesMessages").dictList("messages")

    private fun DemoHarness.sessions(project: String): Dict =
        exchange(0.35, "memory", "project" to project).single("memory")

    @Test
    fun notebookOpensOnTheCheckoutThread() = runTest {
        val demo = DemoHarness(this)
        val chats = demo.exchange(0.4, "notesChats", "project" to "storefront")
            .single("notesChats").dictList("chats")

        assertEquals(
            listOf("chat-1" to "Checkout redesign", "chat-56" to "Ideas", "chat-65" to "General"),
            chats.map { it.string("id") to it.string("title") }
        )
        assertEquals(1_792_088_340_123L, chats[0].long("updatedAt"))
        val blog = demo.exchange(0.4, "notesChats", "project" to "blog").single("notesChats")
        assertEquals(listOf("General"), blog.dictList("chats").map { it.string("title") })
    }

    @Test
    fun notesPageNewestFirstWithAShortLastPage() = runTest {
        val demo = DemoHarness(this)
        val page = demo.page()

        assertEquals(50, page.size)
        assertEquals("note-55", page.first().string("id"))
        assertEquals(
            "Post in the design review channel once the pull request is open.",
            page.first().string("text")
        )
        val older = demo.page(beforeId = page.last().string("id")!!, seconds = 0.5)
        assertEquals(listOf("note-5", "note-4", "note-3", "note-2"), older.map { it.string("id") })
    }

    @Test
    fun theSummaryCardAttachmentRoundTrips() = runTest {
        val demo = DemoHarness(this)
        val attachment = demo.page().single { it.containsKey("attachments") }
            .dictList("attachments").single()
        assertEquals("summary-card.png", attachment.string("name"))
        assertEquals("image/png", attachment.string("mimeType"))

        val hash = attachment.string("hash")!!
        val data = demo.exchange(0.5, "notesAttachment", "project" to "storefront", "hash" to hash)
            .single("notesAttachment").string("data")!!
        val bytes = Base64.getDecoder().decode(data)
        assertEquals(attachment.int("size"), bytes.size)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        assertEquals(hash, digest.joinToString("") { "%02x".format(it) })
    }

    @Test
    fun searchFindsNotesAcrossChats() = runTest {
        val demo = DemoHarness(this)
        val hits = demo.exchange(0.35, "notesSearch", "project" to "storefront", "query" to "MOCK")
            .single("notesSearch").dictList("hits")

        assertEquals(4, hits.size)
        assertTrue(hits.all { it.string("chatTitle") == "Checkout redesign" })
        assertEquals(
            "Mock of the summary card at the mobile breakpoint — this is the spacing we agreed on.",
            hits[0].string("snippet")
        )
    }

    @Test
    fun memoryListsSessionsNewestFirst() = runTest {
        val demo = DemoHarness(this)
        val memory = demo.sessions("storefront")

        assertEquals(true, memory.bool("exists"))
        assertEquals("~/.lpm/memory/storefront", memory.string("dir"))
        assertEquals(
            listOf(
                Triple("checkout-redesign", "Checkout redesign", 1_792_083_300L),
                Triple("payments-module", "Payments module", 1_791_994_200L),
                Triple("cart-badge-bug", "Cart badge not updating", 1_791_669_000L)
            ),
            memory.dictList("sessions").map {
                Triple(it.string("name"), it.string("title"), it.long("updatedAt"))
            }
        )
        assertEquals(listOf(1705L, 741L, 544L), memory.dictList("sessions").map { it.long("size") })
        assertEquals(false, demo.sessions("blog").bool("exists"))
    }

    @Test
    fun memorySessionCarriesTheTimeline() = runTest {
        val demo = DemoHarness(this)
        val content = demo.exchange(
            0.3,
            "memorySession",
            "project" to "storefront",
            "name" to "cart-badge-bug"
        ).single("memorySession").string("content")

        assertEquals(
            "# Cart badge not updating\n\n" +
                "## Goal\nThe header badge misses the first item added from a product page.\n\n" +
                "## Current state\nTraced to the badge reading a snapshot taken before the cart " +
                "store hydrates. A fix is drafted but not committed — reproduce once more on a " +
                "cold load first.\n\n" +
                "## Timeline\n\n" +
                "### 2026-10-10 17:50 — claude\n" +
                "- Done: Reproduced on a cold load; a warm load is always correct\n" +
                "- Learned: The badge subscribes after hydration finishes, so the first change " +
                "is missed\n" +
                "- Next: Subscribe before hydration and seed from the hydrated value\n",
            content
        )
    }

    @Test
    fun memorySaveIsCompareAndSwap() = runTest {
        val demo = DemoHarness(this)
        val stale = demo.exchange(
            0.45,
            "memorySave",
            "project" to "storefront",
            "name" to "checkout-redesign",
            "content" to "x",
            "baseline" to "old"
        ).single("memorySave")
        assertEquals("modified", stale.string("error"))

        val saved = demo.exchange(
            1.1,
            "memorySave",
            "project" to "storefront",
            "name" to "launch-plan",
            "content" to "# Launch plan\nship it",
            "baseline" to ""
        )
        assertEquals(
            listOf(900L to "memorySave", 1500L to "memory-changed"),
            saved.map { it.at to it.t }
        )
        val listed = demo.sessions("storefront").dictList("sessions")
        assertEquals("Launch plan", listed.first().string("title"))
    }
}
