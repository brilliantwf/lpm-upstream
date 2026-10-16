package cx.lpm.mobile.feature.git.review

import cx.lpm.mobile.core.model.GitDiffResult
import cx.lpm.mobile.core.model.GitFile
import cx.lpm.mobile.feature.git.diff.DiffBlockKind
import cx.lpm.mobile.feature.git.diff.ParsedDiff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewRowsTest {
    private fun file(path: String) = GitFile(path, "modified", staged = false, stamp = "")

    private fun review(
        path: String,
        result: GitDiffResult? = null,
        error: String? = null,
        parsed: ParsedDiff? = null,
        collapsed: Boolean = false,
        expanded: Boolean = false
    ) = ReviewFile(file(path), result, error, parsed, true, false, collapsed, expanded)

    @Test
    fun `no changes, with a pull request still possible`() {
        assertEquals(listOf(ReviewRow.NoChanges), reviewRows(emptyList(), ghCli = false))
        assertEquals(
            listOf(ReviewRow.NoChanges, ReviewRow.CreatePullRequest),
            reviewRows(emptyList(), ghCli = true)
        )
    }

    @Test
    fun `each file shows what's loaded for it`() {
        val diff = "@@ -1 +1 @@\n-a\n+b\n"
        val rows = reviewRows(
            listOf(
                review("loading.ts"),
                review("failed.ts", error = "nope"),
                review("binary.png", GitDiffResult("", binary = true, truncated = false)),
                review("empty.ts", GitDiffResult("\n", binary = false, truncated = false)),
                review("parsing.ts", GitDiffResult(diff, binary = false, truncated = false)),
                review("closed.ts", collapsed = true)
            ),
            ghCli = false
        )
        val notes = rows.filterIsInstance<ReviewRow.Note>().associate { it.path to it.note }
        assertEquals(FileNote.Loading, notes["loading.ts"])
        assertEquals(FileNote.Failed("nope"), notes["failed.ts"])
        assertEquals(FileNote.Binary, notes["binary.png"])
        assertEquals(FileNote.Empty, notes["empty.ts"])
        assertEquals(FileNote.Loading, notes["parsing.ts"])
        assertTrue("closed.ts" !in notes)
        assertEquals(ReviewRow.Commit, rows.last())
    }

    @Test
    fun `a long diff stops at whole blocks under 600 lines until shown in full`() {
        val lines = (1..700).joinToString("\n") { "+line $it" }
        val diff = "@@ -0,0 +1,700 @@\n$lines\n@@ -900,1 +1000,1 @@\n-x\n+y\n"
        val parsed = ParsedDiff.parse(diff, "ts")
        val result = GitDiffResult(diff, binary = false, truncated = false)

        val cut = reviewRows(listOf(review("big.ts", result, parsed = parsed)), ghCli = false)
        val blocks = cut.filterIsInstance<ReviewRow.Block>().map { it.block }
        assertEquals(576, blocks.sumOf { it.lineCount })
        assertEquals(DiffBlockKind.Code, blocks.last().kind)
        assertEquals(ReviewRow.ShowAll("big.ts", parsed.lines.size), cut[cut.size - 2])

        val all =
            reviewRows(
                listOf(review("big.ts", result, parsed = parsed, expanded = true)),
                ghCli = false
            )
        assertEquals(parsed.blocks.size, all.count { it is ReviewRow.Block })
        assertTrue(all.none { it is ReviewRow.ShowAll })
    }
}
