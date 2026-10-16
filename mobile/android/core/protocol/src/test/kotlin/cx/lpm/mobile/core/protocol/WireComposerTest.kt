package cx.lpm.mobile.core.protocol

import cx.lpm.mobile.core.model.HistoryCursor
import org.junit.Test

class WireComposerTest {
    @Test
    fun autocompleteAndRecall() {
        assertFrame(
            """{ "t": "slash", "id": "api-1", "project": "api" }""",
            Wire.slash("api-1", "api")
        )
        assertFrame("""{ "t": "mentions", "project": "api" }""", Wire.mentions("api"))
        assertFrame(
            """{ "t": "history", "project": "api", "q": "deploy" }""",
            Wire.history("api", "deploy")
        )
        assertFrame(
            """{ "t": "historyAdd", "project": "api", "id": "api-1", "label": "Terminal 1", "text": "ship it" }""",
            Wire.historyAdd("api", "api-1", "Terminal 1", "ship it")
        )
    }

    @Test
    fun aiActions() {
        assertFrame("""{ "t": "composerActions" }""", Wire.composerActions())
        assertFrame(
            """{ "t": "composerDraft", "id": "api-1", "text": "half" }""",
            Wire.composerDraft("api-1", "half")
        )
        assertFrame(
            """{ "t": "transform", "reqId": "r", "project": "api", "instruction": "polish", "text": "fix bug", "variants": 3 }""",
            Wire.transform("r", "api", "polish", "fix bug", 3)
        )
    }

    @Test
    fun historyQueryOmitsEverythingUnset() {
        assertFrame(
            """{ "t": "historyQuery" }""",
            Wire.historyQuery(null, null, favoritesOnly = false, folder = null, before = null)
        )
        assertFrame(
            """{ "t": "historyQuery" }""",
            Wire.historyQuery("", "", favoritesOnly = false, folder = "", before = null)
        )
        assertFrame(
            """{ "t": "historyQuery", "project": "api", "search": "deploy", "favoritesOnly": true, "folder": "f-1",
                 "before": { "at": 1760000000000, "seq": 4 } }""",
            Wire.historyQuery(
                "api",
                "deploy",
                favoritesOnly = true,
                folder = "f-1",
                before = HistoryCursor(1_760_000_000_000, 4)
            )
        )
    }

    @Test
    fun historySaveDraftOmitsEmptyOptionals() {
        assertFrame(
            """{ "t": "historySaveDraft", "message": "wip" }""",
            Wire.historySaveDraft(
                "wip",
                project = "",
                id = null,
                label = null,
                images = emptyMap()
            )
        )
        assertFrame(
            """{ "t": "historySaveDraft", "message": "see [Image 1]", "project": "api", "id": "api-1", "label": "Terminal 1",
                 "images": { "[Image 1]": "/tmp/a.png" } }""",
            Wire.historySaveDraft(
                "see [Image 1]",
                "api",
                "api-1",
                "Terminal 1",
                mapOf("[Image 1]" to "/tmp/a.png")
            )
        )
    }

    @Test
    fun historyEdits() {
        assertFrame(
            """{ "t": "historyToggleFavorite", "id": "m-1" }""",
            Wire.historyToggleFavorite("m-1")
        )
        assertFrame(
            """{ "t": "historySetFolder", "id": "m-1", "folder": "f-1" }""",
            Wire.historySetFolder("m-1", "f-1")
        )
        assertFrame(
            """{ "t": "historySetFolder", "id": "m-1" }""",
            Wire.historySetFolder("m-1", null)
        )
        assertFrame("""{ "t": "historyDelete", "id": "m-1" }""", Wire.historyDelete("m-1"))
        assertFrame("""{ "t": "historyFolders" }""", Wire.historyFolders())
        assertFrame(
            """{ "t": "historyCreateFolder", "name": "Ideas" }""",
            Wire.historyCreateFolder("Ideas")
        )
        assertFrame(
            """{ "t": "historyDeleteFolder", "id": "f-1" }""",
            Wire.historyDeleteFolder("f-1", null)
        )
        assertFrame(
            """{ "t": "historyDeleteFolder", "name": "Ideas" }""",
            Wire.historyDeleteFolder("", "Ideas")
        )
        assertFrame(
            """{ "t": "historyDeleteFolder", "id": "f-1", "name": "Ideas" }""",
            Wire.historyDeleteFolder("f-1", "Ideas")
        )
    }
}
