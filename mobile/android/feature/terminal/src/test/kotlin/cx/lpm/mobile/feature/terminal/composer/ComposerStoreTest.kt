package cx.lpm.mobile.feature.terminal.composer

import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ComposerStoreTest {
    private val link = FakeLink()
    private val media = FakeMedia(Files.createTempDirectory("composer").toFile())

    private fun TestScope.settle(millis: Long = 0) {
        testScheduler.advanceTimeBy(millis)
        testScheduler.runCurrent()
    }

    @Test
    fun `typing mirrors the draft once the typist pauses`() = runTest {
        val store = composer(link, media)
        store.setText("h")
        store.setText("he")
        settle(100)
        store.setText("hey")
        settle(249)
        assertEquals(emptyList<String>(), link.drafts)
        settle(1)
        assertEquals(listOf("hey"), link.drafts)
    }

    @Test
    fun `a send clears the only tab and mirrors the empty draft at once`() = runTest {
        val store = composer(link, media)
        store.setText("ship it")
        store.afterSend()
        assertEquals("", store.current.text)
        assertEquals(listOf(""), link.drafts)
        settle(1_000)
        assertEquals(listOf(""), link.drafts)
    }

    @Test
    fun `a send closes its tab when others remain`() = runTest {
        val store = composer(link, media)
        store.setText("first")
        store.newTab()
        store.setText("second")
        store.afterSend()
        assertEquals(1, store.current.tabs.size)
        assertEquals("first", store.current.text)
    }

    @Test
    fun `closing a tab before the active one keeps the active tab`() = runTest {
        val store = composer(link, media)
        store.setText("a")
        store.newTab()
        store.setText("b")
        store.newTab()
        store.setText("c")
        store.closeTab(0)
        assertEquals(listOf("b", "c"), store.current.tabs.map { it.text })
        assertEquals("c", store.current.text)
        store.closeTab(1)
        assertEquals("b", store.current.text)
        store.closeTab(0)
        assertEquals(1, store.current.tabs.size)
    }

    @Test
    fun `a remote draft applies by revision and never echoes`() = runTest {
        val store = composer(link, media)
        store.applyRemoteDraft("from mac", rev = 2, origin = "mac", isSeed = false)
        assertEquals("from mac", store.current.text)
        store.applyRemoteDraft("older", rev = 1, origin = "mac", isSeed = false)
        assertEquals("from mac", store.current.text)
        store.applyRemoteDraft("mine", rev = 3, origin = "phone-1", isSeed = false)
        assertEquals("from mac", store.current.text)
        settle(1_000)
        assertEquals(emptyList<String>(), link.drafts)
    }

    @Test
    fun `a remote draft yields to someone typing here`() = runTest {
        val store = composer(link, media)
        store.isEditorFocused = true
        store.setText("typing")
        settle(500)
        store.applyRemoteDraft("remote", rev = 1, origin = "mac", isSeed = false)
        assertEquals("typing", store.current.text)
        settle(1_500)
        store.applyRemoteDraft("remote", rev = 2, origin = "mac", isSeed = false)
        assertEquals("remote", store.current.text)
    }

    @Test
    fun `a seeded draft only fills an empty field`() = runTest {
        val store = composer(link, media)
        store.setText("in progress")
        store.applyRemoteDraft("seeded", rev = 1, origin = "mac", isSeed = true)
        assertEquals("in progress", store.current.text)
        store.setText("")
        store.applyRemoteDraft("seeded", rev = 2, origin = "mac", isSeed = true)
        assertEquals("seeded", store.current.text)
    }

    @Test
    fun `uploads resolve by reqId whatever order replies come in`() = runTest {
        val store = composer(link, media)
        store.addImage(PickedUri("img:one"))
        store.addImage(PickedUri("img:two"))
        assertEquals(SendState.Pending, store.current.sendState)
        settle()
        val (first, second) = link.uploads
        store.resolveUpload(second.reqId, "/uploads/two.jpg")
        store.resolveUpload(first.reqId, "/uploads/one.jpg")
        store.setText("look")
        assertEquals(
            SendState.Ready("look /uploads/one.jpg /uploads/two.jpg"),
            store.current.sendState
        )
    }

    @Test
    fun `an old server's replies resolve the oldest upload first`() = runTest {
        val store = composer(link, media)
        store.addImage(PickedUri("img:one"))
        store.addImage(PickedUri("img:two"))
        settle()
        store.resolveUpload("", "/uploads/a.jpg")
        assertEquals("/uploads/a.jpg", store.current.attachments[0].macPath)
        assertTrue(store.current.attachments[1].isPending)
    }

    @Test
    fun `a failed upload retries with a new reqId and its kept payload`() = runTest {
        val store = composer(link, media)
        store.addImage(PickedUri("img:one"))
        settle()
        val first = link.uploads.single()
        store.resolveUpload(first.reqId, "")
        val chip = store.current.attachments.single()
        assertTrue(chip.isFailed)
        store.setText("hi")
        assertEquals(SendState.DroppedFailures("hi"), store.current.sendState)

        store.retryUpload(chip.id)
        val retry = link.uploads.last()
        assertEquals(first.b64, retry.b64)
        assertTrue(retry.reqId != first.reqId)
        store.resolveUpload(first.reqId, "/late.jpg")
        assertTrue(store.current.attachments.single().isPending)
        store.resolveUpload(retry.reqId, "/ok.jpg")
        assertEquals("/ok.jpg", store.current.attachments.single().macPath)
    }

    @Test
    fun `an unreadable image drops its chip`() = runTest {
        val store = composer(link, media)
        store.addImage(PickedUri("bad:img"))
        assertEquals(1, store.current.attachments.size)
        settle()
        assertEquals(0, store.current.attachments.size)
        assertEquals(SendState.Empty, store.current.sendState)
    }

    @Test
    fun `a dropped link fails what's still uploading`() = runTest {
        val store = composer(link, media)
        store.addImage(PickedUri("img:one"))
        settle()
        store.failInFlightUploads()
        assertEquals(
            Attachment.Status.Failed("Upload interrupted"),
            store.current.attachments.single().status
        )
    }

    @Test
    fun `picks keep their order while they load`() = runTest {
        val store = composer(link, media)
        store.addPicked(listOf(PickedUri("video:clip-4.mov"), PickedUri("img:photo")))
        assertEquals(
            listOf(Attachment.Kind.Video, Attachment.Kind.Image),
            store.current.attachments.map {
                it.kind
            }
        )
        settle()
        assertEquals("clip-4.mov", store.current.attachments[0].filename)
    }

    @Test
    fun `a file goes in pieces, each after the last is acknowledged`() = runTest {
        link.takesFilesInPieces = true
        val store = composer(link, media)
        store.addFile(PickedUri("file:notes-10.txt"))
        settle()
        assertEquals(listOf("begin notes-10.txt 10"), link.steps)
        val upload = store.fileUploads.values.single()

        store.applyUploadStep(step(upload.reqId, uploadId = "u1", chunk = 4))
        settle()
        assertEquals("chunk u1 0 4", link.steps.last())
        store.applyUploadStep(step(upload.reqId, received = 4))
        settle()
        assertEquals("chunk u1 4 4", link.steps.last())
        assertEquals(0.4, store.current.attachments.single().progress!!, 0.001)
        store.applyUploadStep(step("stale", received = 99))
        store.applyUploadStep(step(upload.reqId, received = 8))
        settle()
        assertEquals("chunk u1 8 2", link.steps.last())
        store.applyUploadStep(step(upload.reqId, received = 10))
        settle()
        assertEquals("done u1", link.steps.last())
        store.applyUploadStep(step(upload.reqId, path = "/uploads/notes-10.txt"))

        assertEquals("/uploads/notes-10.txt", store.current.attachments.single().macPath)
        assertEquals(1, media.discarded.size)
        assertTrue(store.fileUploads.isEmpty())
    }

    @Test
    fun `a refused piece fails the chip with the Mac's reason and frees the partial file`() =
        runTest {
            link.takesFilesInPieces = true
            val store = composer(link, media)
            store.addFile(PickedUri("file:big-10.bin"))
            settle()
            val upload = store.fileUploads.values.single()
            store.applyUploadStep(step(upload.reqId, uploadId = "u9", chunk = 4))
            settle()
            store.applyUploadStep(step(upload.reqId, ok = false, error = "disk is full"))
            assertTrue(store.current.attachments.single().isFailed)
            assertEquals("Disk is full", store.current.uploadError)
            assertEquals("abort u9", link.steps.last())
        }

    @Test
    fun `a piece that never gets an answer fails after a minute`() = runTest {
        link.takesFilesInPieces = true
        val store = composer(link, media)
        store.addFile(PickedUri("file:slow-10.bin"))
        settle(59_999)
        assertTrue(store.current.attachments.single().isPending)
        settle(2)
        assertTrue(store.current.attachments.single().isFailed)
        assertEquals(null, store.current.uploadError)
    }

    @Test
    fun `an older Mac takes a small file in one frame under its name`() = runTest {
        val store = composer(link, media)
        store.addFile(PickedUri("file:tiny-3.txt"))
        settle()
        val upload = link.uploads.single()
        assertEquals("tiny-3.txt", upload.name)
        assertEquals("AAEC", upload.b64)
    }

    @Test
    fun `an older Mac can't take a file over eight megabytes`() = runTest {
        val store = composer(link, media)
        store.addFile(PickedUri("file:movie-9437184.bin"))
        settle()
        assertEquals(0, store.current.attachments.size)
        assertEquals(
            "movie-9437184.bin is 9 MB. Update lpm on your Mac to attach files over 8 MB from your phone.",
            store.current.uploadError
        )
    }

    private fun step(
        reqId: String,
        ok: Boolean = true,
        error: String? = null,
        uploadId: String = "",
        chunk: Long = 0,
        received: Long = 0,
        path: String = ""
    ) = cx.lpm.mobile.core.model.UploadStep("t1", reqId, ok, error, uploadId, chunk, received, path)
}
