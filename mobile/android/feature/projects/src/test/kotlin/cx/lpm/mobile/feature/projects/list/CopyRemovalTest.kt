package cx.lpm.mobile.feature.projects.list

import cx.lpm.mobile.core.model.GitFile
import cx.lpm.mobile.core.model.GitSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class CopyRemovalTest {
    private fun snapshot(
        files: List<GitFile> = emptyList(),
        hasUpstream: Boolean = true,
        ahead: Int = 0,
        aheadOfDefault: Int? = null,
        isRepo: Boolean = true
    ) = GitSnapshot(
        isRepo = isRepo,
        branch = "lpm/storefront-2",
        detached = false,
        hasUpstream = hasUpstream,
        ahead = ahead,
        behind = 0,
        defaultBranch = "main",
        aheadOfDefault = aheadOfDefault,
        ghCli = false,
        files = files,
        autoPulled = null
    )

    private fun file(path: String, status: String, staged: Boolean = false) =
        GitFile(path, status, staged, "")

    @Test
    fun `changes, untracked files and unpushed commits are the work only the copy has`() {
        val s = snapshot(
            files = listOf(
                file("a.ts", "modified", staged = true),
                file("a.ts", "modified"),
                file("b.ts", "deleted"),
                file("new.txt", "untracked")
            ),
            ahead = 3
        )
        assertEquals(
            listOf(UnsavedWork.Changed(2), UnsavedWork.Untracked(1), UnsavedWork.NotPushed(3)),
            unsavedWork(s)
        )
    }

    @Test
    fun `without an upstream the commits count against the default branch`() {
        assertEquals(
            listOf(UnsavedWork.NotOnDefault(2, "main")),
            unsavedWork(snapshot(hasUpstream = false, ahead = 5, aheadOfDefault = 2))
        )
        assertEquals(emptyList<UnsavedWork>(), unsavedWork(snapshot(hasUpstream = false)))
        assertEquals(emptyList<UnsavedWork>(), unsavedWork(snapshot(isRepo = false, ahead = 1)))
    }

    @Test
    fun `a worktree's branch is named as lpm makes it`() {
        assertEquals("lpm/storefront-2", worktreeBranchName("storefront 2"))
        assertEquals("lpm/a-b_c", worktreeBranchName("--a..b_c--"))
        assertEquals("lpm/caf", worktreeBranchName("café"))
        assertEquals("lpm/worktree", worktreeBranchName("✨"))
    }
}
