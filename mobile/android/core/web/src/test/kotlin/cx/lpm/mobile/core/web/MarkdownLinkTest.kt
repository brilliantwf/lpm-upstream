package cx.lpm.mobile.core.web

import cx.lpm.mobile.core.web.MarkdownLink.Anchor
import cx.lpm.mobile.core.web.MarkdownLink.External
import cx.lpm.mobile.core.web.MarkdownLink.File
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownLinkTest {
    private fun resolve(href: String, from: String = "docs/guide/README.md") =
        MarkdownLink.resolve(href, from)

    @Test
    fun linksWithASchemeLeaveTheApp() {
        assertEquals(External("https://lpm.cx/docs"), resolve("https://lpm.cx/docs"))
        assertEquals(External("mailto:hi@lpm.cx"), resolve("mailto:hi@lpm.cx"))
        assertEquals(External("x-git+ssh.1:repo"), resolve("x-git+ssh.1:repo"))
        assertEquals(External("https://cdn.example.com/a.png"), resolve("//cdn.example.com/a.png"))
    }

    @Test
    fun fragmentsAndQueriesAloneStayOnThePage() {
        assertEquals(Anchor, resolve("#setup"))
        assertEquals(Anchor, resolve("?tab=2"))
        assertEquals(Anchor, resolve(""))
    }

    @Test
    fun relativePathsResolveAgainstTheFilesFolder() {
        assertEquals(File(listOf("docs/guide/setup.md")), resolve("setup.md#install"))
        assertEquals(File(listOf("docs/guide/img/a.png")), resolve("./img/./a.png?raw=1"))
        assertEquals(File(listOf("docs/api.md")), resolve("../api.md"))
        assertEquals(File(listOf("LICENSE")), resolve("../../LICENSE"))
        assertEquals(File(listOf("../x.md")), resolve("../../../x.md"))
        assertEquals(File(listOf("notes.md")), resolve("notes.md", from = "README.md"))
        assertEquals(File(listOf("1:2.md")), resolve("1:2.md", from = ""))
    }

    @Test
    fun aLeadingSlashReadsAsTheProjectRootFirst() {
        assertEquals(File(listOf("docs/a.png", "/docs/a.png")), resolve("/docs/a.png"))
        assertEquals(File(listOf("../a.md", "/a.md")), resolve("/../a.md"))
    }

    @Test
    fun absoluteFilesResolveAgainstTheirFolder() {
        assertEquals(File(listOf("/Users/demo/b.md")), resolve("b.md", from = "/Users/demo/a.md"))
        assertEquals(File(listOf("/b.md")), resolve("b.md", from = "/a.md"))
        assertEquals(File(listOf("/b.md")), resolve("../b.md", from = "/a.md"))
    }

    @Test
    fun pathsArePercentDecoded() {
        assertEquals(File(listOf("docs/guide/my image.png")), resolve("my%20image.png"))
        assertEquals(File(listOf("docs/guide/café.md")), resolve("caf%C3%A9.md"))
        assertEquals(File(listOf("docs/guide/100%.md")), resolve("100%.md"))
        assertEquals(File(listOf("docs/guide/bad%zz.md")), resolve("bad%zz.md"))
        assertEquals(File(listOf("docs/guide/a%FF.md")), resolve("a%FF.md"))
    }

    @Test
    fun aTildeIsNeverClimbedOut() {
        assertEquals(File(listOf("~/../x.md")), resolve("~/../x.md", from = ""))
    }
}
