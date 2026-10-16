package cx.lpm.mobile.core.protocol

import cx.lpm.mobile.core.model.BackgroundRunSummary
import cx.lpm.mobile.core.model.ControlOwner
import cx.lpm.mobile.core.model.UploadStep
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InboundTerminalsTest {
    @Test
    fun terminals() {
        val f = decode<Inbound.Terminals>(
            """{ "t": "terminals", "project": "storefront", "terminals": [
                 { "id": "storefront-1", "label": "Terminal 1", "project": "storefront", "cols": 120, "rows": 40,
                   "remote": false, "pinned": true, "emoji": "🛠", "cli": "claude" },
                 { "id": "storefront-2", "project": "storefront" }] }"""
        )
        val (first, second) = f.terminals
        assertEquals("Terminal 1", first.label)
        assertEquals(120, first.cols)
        assertTrue(first.pinned)
        assertEquals("claude", first.cli)
        assertEquals("storefront-2", second.label)
        assertEquals(80, second.cols)
    }

    @Test
    fun seed() {
        val f = decode<Inbound.Seed>(
            """{ "t": "seed", "id": "storefront-1", "cols": 100, "rows": 30, "data": "${'$'} ls\r\n", "off": 5000000000,
                 "reset": false, "owner": { "kind": "mobile", "id": "d-1", "label": "My iPhone" },
                 "draft": { "text": "fix the bug", "rev": 7 } }"""
        )
        assertEquals("\$ ls\r\n", f.data)
        assertEquals(5_000_000_000L, f.off)
        assertEquals(false, f.reset)
        assertEquals(ControlOwner("mobile", "d-1", "My iPhone"), f.owner)
        assertEquals("fix the bug", f.draftText)
        assertEquals(7, f.draftRev)
    }

    @Test
    fun seedFromAnOlderMac() {
        assertEquals(
            Inbound.Seed("t-1", 80, 24, "", null, null, 0, null, true),
            Inbound.parse("""{ "t": "seed", "id": "t-1", "owner": null }""")
        )
    }

    @Test
    fun controlOutputAndExit() {
        assertEquals(
            Inbound.Control("t-1", ControlOwner("window", "main", "Main window")),
            Inbound.parse(
                """{ "t": "control", "id": "t-1", "owner": { "kind": "window", "id": "main", "label": "Main window" } }"""
            )
        )
        assertEquals(
            Inbound.Control("t-1", null),
            Inbound.parse("""{ "t": "control", "id": "t-1", "owner": null }""")
        )
        assertEquals(
            Inbound.Output("t-1", "hi", 42),
            Inbound.parse("""{ "t": "o", "id": "t-1", "d": "hi", "off": 42 }""")
        )
        assertEquals(
            Inbound.Output("t-1", "hi", null),
            Inbound.parse("""{ "t": "o", "id": "t-1", "d": "hi" }""")
        )
        assertEquals(
            Inbound.Exit("t-1", -1, true),
            Inbound.parse("""{ "t": "exit", "id": "t-1", "code": -1, "gone": true }""")
        )
        assertEquals(
            Inbound.Exit("t-1", 0, false),
            Inbound.parse("""{ "t": "exit", "id": "t-1" }""")
        )
    }

    @Test
    fun runActionAndNewTerminalOnlySurfaceFailures() {
        for (t in listOf("runAction", "newTerminal")) {
            assertNull(Inbound.parse("""{ "t": "$t", "ok": true }"""))
            assertNull(Inbound.parse("""{ "t": "$t" }"""))
            assertEquals(
                Inbound.ActionFailed("api", "Couldn't reach the lpm app on your Mac."),
                Inbound.parse("""{ "t": "$t", "ok": false, "project": "api" }""")
            )
        }
        assertEquals(
            Inbound.ActionFailed("", "App closed"),
            Inbound.parse("""{ "t": "runAction", "ok": false, "error": "App closed" }""")
        )
    }

    @Test
    fun actionBgOutput() {
        val found = decode<Inbound.ActionBgOutput>(
            """{ "t": "actionBgOutput", "ok": true, "found": true, "runId": "r-1", "project": "api", "label": "Seed DB",
                 "startedAt": 1760000000, "text": "done\n", "running": false, "success": true, "error": "" }"""
        )
        assertEquals("r-1", found.runId)
        assertEquals("Seed DB", found.snapshot!!.label)
        assertTrue(found.snapshot.success)
        assertEquals(
            Inbound.ActionBgOutput("r-1", null),
            Inbound.parse(
                """{ "t": "actionBgOutput", "ok": true, "found": false, "runId": "r-1" }"""
            )
        )
    }

    @Test
    fun runActionBackgroundOnlySurfacesFailures() {
        assertNull(Inbound.parse("""{ "t": "runActionBackground", "ok": true }"""))
        assertEquals(
            Inbound.ActionBgStartFailed("r-2", "Couldn't start the action."),
            Inbound.parse("""{ "t": "runActionBackground", "ok": false, "runId": "r-2" }""")
        )
    }

    @Test
    fun backgroundRuns() {
        val f = decode<Inbound.BackgroundRuns>(
            """{ "t": "backgroundRuns", "ok": true, "project": "api",
                 "runs": [{ "runId": "r-1", "label": "Seed DB", "startedAt": 1760000000, "running": true }] }"""
        )
        assertEquals(
            BackgroundRunSummary("r-1", "Seed DB", 1_760_000_000, true, false, ""),
            f.runs.single()
        )
    }

    @Test
    fun upload() {
        assertEquals(
            Inbound.Upload("t-1", "req-1", "/tmp/lpm/a.png"),
            Inbound.parse(
                """{ "t": "upload", "id": "t-1", "ok": true, "path": "/tmp/lpm/a.png", "reqId": "req-1" }"""
            )
        )
        assertEquals(
            Inbound.Upload("t-1", "17", ""),
            Inbound.parse(
                """{ "t": "upload", "id": "t-1", "ok": false, "path": "x", "reqId": 17 }"""
            )
        )
    }

    @Test
    fun chunkedUploadSteps() {
        assertEquals(
            Inbound.UploadStep(UploadStep("t-1", "q", true, null, "u-1", 1_048_576, 0, "")),
            Inbound.parse(
                """{ "t": "uploadBegin", "id": "t-1", "ok": true, "uploadId": "u-1", "chunk": 1048576, "reqId": "q" }"""
            )
        )
        assertEquals(
            3_000_000_000L,
            decode<Inbound.UploadStep>(
                """{ "t": "uploadChunk", "id": "t-1", "ok": true, "received": 3000000000, "reqId": "q" }"""
            ).step.received
        )
        val done =
            decode<Inbound.UploadStep>(
                """{ "t": "uploadDone", "id": "t-1", "ok": false, "error": "disk full", "reqId": 5 }"""
            )
        assertEquals("disk full", done.step.error)
        assertEquals("5", done.step.reqId)
    }

    @Test
    fun fileChunk() {
        val f = decode<Inbound.FileChunk>(
            """{ "t": "fileChunk", "ok": true, "reqId": "f-1", "path": "/Users/me/a.txt", "size": 2,
                 "mtime": 1760000000000, "offset": 0, "data": "aGk=" }"""
        )
        assertArrayEquals("hi".toByteArray(), f.chunk.data)
        assertEquals("/Users/me/a.txt", f.chunk.path)
        val missing =
            decode<Inbound.FileChunk>(
                """{ "t": "fileChunk", "ok": false, "reqId": "f-1", "missing": true, "error": "not found" }"""
            )
        assertTrue(missing.chunk.missing)
        assertEquals("not found", missing.chunk.error)
    }
}
