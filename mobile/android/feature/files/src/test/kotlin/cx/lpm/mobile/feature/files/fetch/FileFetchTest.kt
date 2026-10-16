package cx.lpm.mobile.feature.files.fetch

import cx.lpm.mobile.core.model.numberLong
import cx.lpm.mobile.core.model.string
import cx.lpm.mobile.feature.files.fetch.FileFetch.Companion.CHUNK_SIZE
import cx.lpm.mobile.feature.files.fetch.FileFetch.Companion.STALL_TIMEOUT_MS
import cx.lpm.mobile.feature.files.fetch.FileFetch.Companion.TEXT_CAP
import cx.lpm.mobile.feature.files.fetch.FileFetch.Phase
import java.io.File
import java.nio.file.Files
import kotlin.random.Random
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileFetchTest {
    private fun TestScope.harness() = FetchHarness(this)

    private fun binary(size: Int) = Random(1).nextBytes(size).also { it[0] = 0 }

    private val FileFetch.phase get() = state.value.phase

    private fun FileFetch.ready() = phase as Phase.Ready

    @Test
    fun `the first range asks for 256 KB under the fetch's token`() = runTest {
        val h = harness()
        h.fetch("README.md")
        val first = h.requests.single()
        assertEquals("f1:0", first.string("reqId"))
        assertEquals("storefront", first.string("project"))
        assertEquals("README.md", first.string("path"))
        assertEquals(0L, first.numberLong("offset"))
        assertEquals(CHUNK_SIZE, first.numberLong("length"))
    }

    @Test
    fun `a small text file is ready after its one range`() = runTest {
        val h = harness()
        val fetch = h.fetch("notes/todo.md")
        h.reply(h.requests.single(), "# Todo\n- ship".toByteArray())
        val ready = fetch.ready()
        assertEquals(FilePreviewKind.Markdown, ready.kind)
        assertFalse(ready.truncated)
        assertEquals("todo.md", ready.file.name)
        assertEquals("# Todo\n- ship", ready.file.readText())
        assertEquals(13L, fetch.state.value.received)
        assertEquals(13L, fetch.state.value.total)
    }

    @Test
    fun `a large file keeps eight ranges in flight and writes each at its own offset`() = runTest {
        val h = harness()
        val bytes = binary((CHUNK_SIZE * 12 + 1000).toInt())
        val fetch = h.fetch("build/app.bin")
        h.reply(h.requests.single(), bytes)
        assertEquals(bytes.size.toLong(), fetch.state.value.total)
        val window = h.requests.drop(1)
        assertEquals(8, window.size)
        assertEquals((1..8).map { it * CHUNK_SIZE }, window.map { it.numberLong("offset") })
        h.serve(bytes, mutableSetOf("f1:0"))
        val ready = fetch.ready()
        assertEquals(FilePreviewKind.Document, ready.kind)
        assertArrayEquals(bytes, ready.file.readBytes())
        assertEquals(bytes.size.toLong(), fetch.state.value.received)
        assertEquals(1000L, h.requests.last().numberLong("length"))
    }

    @Test
    fun `a reply for another fetch or a range never asked for is ignored`() = runTest {
        val h = harness()
        val fetch = h.fetch("a.txt")
        val first = h.requests.single()
        h.reply(first.withReqId("f9:0"), "nope".toByteArray())
        h.reply(first.withOffset(CHUNK_SIZE), "nope".toByteArray())
        assertEquals(Phase.Loading, fetch.phase)
        h.reply(first, "yes".toByteArray())
        assertEquals("yes", fetch.ready().file.readText())
    }

    @Test
    fun `text past 1 MB fetches only its head and says so`() = runTest {
        val h = harness()
        val text = ByteArray((TEXT_CAP + CHUNK_SIZE * 2).toInt()) { 'a'.code.toByte() }
        val fetch = h.fetch("logs/big.log")
        h.serve(text)
        val ready = fetch.ready()
        assertTrue(ready.truncated)
        assertEquals(TEXT_CAP, ready.file.length())
        assertEquals(TEXT_CAP, fetch.state.value.total)
        assertTrue(h.requests.all { it.numberLong("offset")!! < TEXT_CAP })
    }

    @Test
    fun `a file over 1 GB isn't fetched`() = runTest {
        val h = harness()
        val fetch = h.fetch("movie.bin")
        h.reply(h.requests.single(), binary(16), size = FileFetch.MAX_BYTES + 1)
        assertEquals(
            Phase.Failed("This file is over 1 GB — too large to open on this phone."),
            fetch.phase
        )
        assertEquals(1, h.requests.size)
    }

    @Test
    fun `a missing reading moves on to the next, and the last one's error stands`() = runTest {
        val h = harness()
        val fetch = h.fetch("src/a.ts", "a.ts")
        h.replyError(h.requests.single(), "No such file", missing = true)
        val second = h.requests.last()
        assertEquals("f2:0", second.string("reqId"))
        assertEquals("a.ts", second.string("path"))
        assertEquals("a.ts", fetch.state.value.path)
        h.replyError(second, "No such file", missing = true)
        assertEquals(Phase.Failed("No such file"), fetch.phase)

        fetch.retry()
        assertEquals("src/a.ts", h.requests.last().string("path"))
        assertEquals(Phase.Loading, fetch.phase)
    }

    @Test
    fun `an error that isn't a missing file fails at once`() = runTest {
        val h = harness()
        val fetch = h.fetch("secret.txt", "other.txt")
        h.replyError(h.requests.single(), "Permission denied")
        assertEquals(Phase.Failed("Permission denied"), fetch.phase)
        assertEquals(1, h.requests.size)
    }

    @Test
    fun `a binary file that changes between ranges fails, a text one carries on`() = runTest {
        val h = harness()
        val bytes = binary((CHUNK_SIZE * 2).toInt())
        val fetch = h.fetch("a.bin")
        h.reply(h.requests.single(), bytes)
        h.reply(h.requests[1], bytes, mtime = 2)
        assertEquals(Phase.Failed("The file changed while it was loading."), fetch.phase)

        val text = ByteArray((CHUNK_SIZE * 2).toInt()) { 'x'.code.toByte() }
        val log = h.fetch("a.log")
        h.reply(h.requests.last(), text)
        h.reply(h.requests.last(), text, mtime = 2)
        assertEquals(FilePreviewKind.Text, log.ready().kind)
    }

    @Test
    fun `a file that shrank ends where its data does`() = runTest {
        val h = harness()
        val bytes = "y".repeat((CHUNK_SIZE + 100).toInt()).toByteArray()
        val fetch = h.fetch("a.txt")
        h.reply(h.requests.single(), bytes, size = CHUNK_SIZE * 3)
        assertEquals(CHUNK_SIZE * 3, fetch.state.value.total)
        h.serve(bytes, mutableSetOf("f1:0"))
        assertEquals(CHUNK_SIZE + 100, fetch.state.value.total)
        assertEquals(CHUNK_SIZE + 100, fetch.ready().file.length())
    }

    @Test
    fun `an unanswered range is asked again, and three silent rounds give up`() = runTest {
        val h = harness()
        val fetch = h.fetch("a.txt")
        h.advance(STALL_TIMEOUT_MS + 1)
        assertEquals(listOf("f1:0", "f1:0"), h.requests.map { it.string("reqId") })
        h.advance(STALL_TIMEOUT_MS)
        assertEquals(3, h.requests.size)
        assertEquals(Phase.Loading, fetch.phase)
        h.advance(STALL_TIMEOUT_MS)
        assertEquals(
            Phase.Failed("Your Mac didn't send the file. Make sure lpm on your Mac is up to date."),
            fetch.phase
        )
    }

    @Test
    fun `a Mac that stops partway says so`() = runTest {
        val h = harness()
        val bytes = binary((CHUNK_SIZE * 3).toInt())
        val fetch = h.fetch("a.bin")
        h.reply(h.requests.single(), bytes)
        h.advance(STALL_TIMEOUT_MS * 3 + 1)
        assertEquals(Phase.Failed("Your Mac stopped sending the file."), fetch.phase)
    }

    @Test
    fun `the watchdog waits while the link is down`() = runTest {
        val h = harness()
        val fetch = h.fetch("a.txt")
        h.socket.fail()
        h.settle()
        h.advance(STALL_TIMEOUT_MS * 5)
        assertEquals(Phase.Loading, fetch.phase)
    }

    @Test
    fun `a dropped link asks again for every range in flight`() = runTest {
        val h = harness()
        h.fetch("a.txt")
        h.fetches.onConnectionReset()
        assertEquals(listOf("f1:0", "f1:0"), h.requests.map { it.string("reqId") })
    }

    @Test
    fun `switching Macs fails every open preview`() = runTest {
        val h = harness()
        val a = h.fetch("a.txt")
        val b = h.fetch("b.txt")
        h.fetches.reset()
        val gone = Phase.Failed("Disconnected from the Mac this file is on.")
        assertEquals(gone, a.phase)
        assertEquals(gone, b.phase)
    }

    @Test
    fun `closing deletes the copy and stops listening`() = runTest {
        val h = harness()
        val fetch = h.fetch("a.txt")
        h.reply(h.requests.single(), "hi".toByteArray())
        val file = fetch.ready().file
        assertTrue(file.exists())
        fetch.close()
        h.settle()
        assertFalse(file.parentFile!!.exists())
    }

    @Test
    fun `awaiting a fetch returns how it ended, and cancelling closes it`() = runTest {
        val h = harness()
        val image = h.fetches.fetch("storefront", listOf("docs/shot.png"))
        val done = async { image.awaitDone() }
        h.settle()
        h.reply(h.requests.single(), binary(64))
        assertEquals(FilePreviewKind.Document, (done.await() as Phase.Ready).kind)

        val slow = h.fetches.fetch("storefront", listOf("docs/slow.png"))
        val waiting = async { slow.awaitDone() }
        h.settle()
        waiting.cancel()
        h.settle()
        h.reply(h.requests.last(), binary(64))
        assertEquals(Phase.Loading, slow.phase)
    }

    @Test
    fun `scratch copies left by an earlier run are removed`() = runTest {
        val root = Files.createTempDirectory("lpm-old").toFile()
        val stale = File(root, "old-token/old.txt").apply {
            parentFile!!.mkdirs()
            writeText("stale")
        }
        ScratchSpace(root, StandardTestDispatcher(testScheduler), backgroundScope)
        testScheduler.runCurrent()
        assertFalse(stale.exists())
    }
}

private fun JsonObject.withReqId(reqId: String) =
    JsonObject(this + ("reqId" to JsonPrimitive(reqId)))

private fun JsonObject.withOffset(offset: Long) =
    JsonObject(this + ("offset" to JsonPrimitive(offset)))
