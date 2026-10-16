package cx.lpm.mobile.feature.terminal.composer

import cx.lpm.mobile.core.model.GitBranch
import cx.lpm.mobile.core.model.MentionEntry
import cx.lpm.mobile.core.model.ServiceInfo
import cx.lpm.mobile.core.model.SlashCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComposerTriggersTest {
    private val commands = listOf(
        SlashCommand("review", "Review a pull request", "", "builtin"),
        SlashCommand("prompts:draftpr", "Draft a PR", "<branch>", "user")
    )

    @Test
    fun `a slash after whitespace or at the start is a command`() {
        assertEquals("", slashQuery("/"))
        assertEquals("rev", slashQuery("please /rev"))
        assertNull(slashQuery("src/app"))
        assertNull(slashQuery("/review now"))
        assertEquals(listOf("review"), slashMatches("/REV", commands).map { it.name })
        assertEquals(2, slashMatches("/", commands).size)
    }

    @Test
    fun `a complete command and one space shows its argument hint`() {
        assertEquals("<branch>", slashArgumentHint("/prompts:draftpr ", commands))
        assertNull(slashArgumentHint("/prompts:draftpr", commands))
        assertNull(slashArgumentHint("/review ", commands))
        assertNull(slashArgumentHint("x/prompts:draftpr ", commands))
    }

    @Test
    fun `an at sign after whitespace starts a mention`() {
        assertEquals("app", mentionQuery("look at @app"))
        assertNull(mentionQuery("me@example.com"))
        assertNull(mentionQuery("@app done"))
        assertEquals("", mentionQuery("@"))
    }

    @Test
    fun `mentions put changes first and show branches only for a fragment`() {
        val entries = listOf(
            MentionEntry("app", dir = true, changed = false),
            MentionEntry("app/page.tsx", dir = false, changed = true)
        )
        val branches = listOf(GitBranch("app-redesign", "", ""), GitBranch("main", "", "origin"))
        val services = listOf(
            ServiceInfo("web", paneIndex = 0, running = true, cmd = "", port = 3000),
            ServiceInfo("worker", paneIndex = null, running = true, cmd = "", port = 0),
            ServiceInfo("api", paneIndex = 1, running = false, cmd = "", port = 0)
        )
        val all = mentionResults("", entries, branches, services)
        assertEquals(listOf("app/page.tsx"), all.changed.map { it.path })
        assertEquals(listOf("app"), all.files.map { it.path })
        assertEquals(emptyList<GitBranch>(), all.branches)
        assertEquals(listOf("web"), all.services.map { it.name })

        val app = mentionResults("app", entries, branches, services)
        assertEquals(listOf("app-redesign"), app.branches.map { it.name })
        assertEquals(emptyList<ServiceInfo>(), app.services)
    }

    @Test
    fun `picking completes the fragment and parks invocations after the text`() {
        assertEquals("run /review ", replacingFragment("run /rev", '/', "/review "))
        assertEquals("see @app/page.tsx ", replacingFragment("see @ap", '@', "@app/page.tsx "))
        assertEquals("/memory load", joined("  ", "/memory load"))
        assertEquals("fix it /memory load", joined("fix it ", "/memory load"))
        assertEquals("\n\nLogs:\n```\nok\n```\n", contextBlock("Logs", "  ok \n"))
    }

    @Test
    fun `tab previews read like the desktop's`() {
        assertEquals("Empty", ComposerTab().preview)
        assertEquals(
            "2 attachments",
            ComposerTab(
                attachments = List(2) {
                    Attachment(Attachment.Kind.File, "f", "m")
                }
            ).preview
        )
        assertEquals(
            "abcdefghijklmnopqrstuvwx",
            ComposerTab(text = "  abcdefghijklmnopqrstuvwxyz").preview
        )
        assertEquals("😀".repeat(24), ComposerTab(text = "😀".repeat(30)).preview)
    }
}
