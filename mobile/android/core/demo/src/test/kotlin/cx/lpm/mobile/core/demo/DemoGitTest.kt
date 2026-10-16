package cx.lpm.mobile.core.demo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoGitTest {
    @Test
    fun statusArrivesAfterASimulatedDelay() = runTest {
        val demo = DemoHarness(this)
        val frames = demo.exchange(0.3, "git", "project" to "storefront")
        val git = frames.single("git")

        assertEquals(300L, frames.single().at)
        assertEquals("feat/checkout-redesign", git.string("branch"))
        assertEquals(2, git.int("ahead"))
        assertEquals(
            listOf(
                "components/CheckoutForm.tsx" to "modified",
                "app/checkout/page.tsx" to "modified",
                "lib/payments.ts" to "added",
                "README.md" to "modified"
            ),
            git.dictList("files").map { it.string("path") to it.string("status") }
        )
        assertEquals(null, git["autoPulled"])
        val gateway = demo.exchange(0.3, "git", "project" to "api-gateway").single("git")
        assertEquals(3, gateway.sub("autoPulled").int("count"))
    }

    @Test
    fun diffsComeFromTheCannedFixtures() = runTest {
        val demo = DemoHarness(this)
        val diff = demo.request("gitDiff", "project" to "storefront", "path" to "lib/payments.ts")
            .single("gitDiff").string("diff")!!

        assertTrue(
            diff.startsWith(
                "diff --git a/lib/payments.ts b/lib/payments.ts\nnew file mode 100644\n"
            )
        )
        assertTrue(diff.endsWith("+  return res.json()\n+}"))
        val generic = demo.request("gitDiff", "project" to "blog", "path" to "src/a.ts")
            .single("gitDiff").string("diff")!!
        assertTrue(generic.contains("\n-const enabled = false\n+const enabled = true\n"))
    }

    @Test
    fun branchesSortNewestFirstWithIsoDates() = runTest {
        val demo = DemoHarness(this)
        val branches = demo.request("gitBranches", "project" to "storefront").single("gitBranches")
            .dictList("branches")

        assertEquals(
            listOf(
                mapOf(
                    "name" to "feat/checkout-redesign",
                    "committerDate" to "2026-10-15T18:30:00Z"
                ),
                mapOf("name" to "main", "committerDate" to "2026-10-12T18:30:00Z"),
                mapOf(
                    "name" to "main",
                    "committerDate" to "2026-10-12T18:30:00Z",
                    "remote" to "origin"
                ),
                mapOf("name" to "fix/cart-badge", "committerDate" to "2026-10-10T18:30:00Z")
            ),
            branches
        )
    }

    @Test
    fun commitNotifiesWatchers() = runTest {
        val demo = DemoHarness(this)
        demo.request("gitWatch", "project" to "storefront")
        val frames = demo.exchange(
            1.0,
            "gitCommit",
            "project" to "storefront",
            "message" to "wip",
            "files" to listOf("README.md")
        )

        assertEquals(
            listOf(400L to "gitCommit", 500L to "git-changed"),
            frames.map { it.at to it.t }
        )
        val git = demo.exchange(0.3, "git", "project" to "storefront").single("git")
        assertEquals(3, git.int("ahead"))
        assertEquals(3, git.dictList("files").size)
    }
}
