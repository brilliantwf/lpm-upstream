package cx.lpm.mobile.feature.terminal.composer

import java.nio.file.Files
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ComposerTransformTest {
    private val link = FakeLink()
    private val media = FakeMedia(Files.createTempDirectory("composer").toFile())

    private fun TestScope.settle(millis: Long = 0) {
        testScheduler.advanceTimeBy(millis)
        testScheduler.runCurrent()
    }

    @Test
    fun `nothing typed starts no rewrite`() = runTest {
        val store = composer(link, media)
        store.startTransform("Improve", variants = 1)
        assertFalse(store.current.transforming)
        assertTrue(link.transforms.isEmpty())
    }

    @Test
    fun `a single rewrite lands on the tab it started on`() = runTest {
        val store = composer(link, media)
        store.setText("draft one")
        store.startTransform("Improve", variants = 0)
        val run = link.transforms.single()
        assertEquals(1, run.variants)
        assertTrue(store.current.transforming)
        store.newTab()
        store.receiveTransformVariant(run.reqId, 0, "Draft one, improved.", null)
        assertFalse(store.current.showVariants)
        store.finishTransform(run.reqId, ok = true)
        assertFalse(store.current.transforming)
        assertEquals("Draft one, improved.", store.current.tabs[0].text)
        assertEquals("", store.current.text)
        assertEquals(listOf<String?>(run.reqId), link.clearedRoutes)
    }

    @Test
    fun `several variants open the picker on the first and stay open`() = runTest {
        val store = composer(link, media)
        store.setText("draft")
        store.startTransform("Shorter", variants = 9)
        val reqId = link.transforms.single().reqId
        assertEquals(5, link.transforms.single().variants)
        store.receiveTransformVariant(reqId, 0, "a", null)
        assertTrue(store.current.showVariants)
        store.variantsSheetDismissed()
        assertFalse(store.current.transforming)
        store.receiveTransformVariant(reqId, 1, "b", null)
        assertFalse(store.current.showVariants)
    }

    @Test
    fun `a picked variant replaces the text and closes the picker`() = runTest {
        val store = composer(link, media)
        store.setText("draft")
        store.startTransform("Shorter", variants = 2)
        val reqId = link.transforms.single().reqId
        store.receiveTransformVariant(reqId, 0, "a", null)
        store.receiveTransformVariant(reqId, 1, null, "model busy")
        store.finishTransform(reqId, ok = true)
        assertTrue(store.current.showVariants)
        assertEquals(2, store.current.variants.size)
        store.applyVariant("a, edited")
        assertEquals("a, edited", store.current.text)
        assertFalse(store.current.showVariants)
        assertTrue(store.current.variants.isEmpty())
    }

    @Test
    fun `a failed or empty rewrite says so`() = runTest {
        val store = composer(link, media)
        store.setText("draft")
        store.startTransform("Improve", variants = 1)
        store.finishTransform(link.transforms.last().reqId, ok = true)
        assertEquals("The rewrite couldn’t be completed. Try again.", store.current.transformError)
        store.dismissTransformError()
        store.startTransform("Improve", variants = 1)
        store.finishTransform(link.transforms.last().reqId, ok = false)
        assertEquals("The rewrite couldn’t be completed. Try again.", store.current.transformError)
        assertEquals("draft", store.current.text)
    }

    @Test
    fun `a cancelled rewrite ignores its late replies`() = runTest {
        val store = composer(link, media)
        store.setText("draft")
        store.startTransform("Improve", variants = 1)
        val reqId = link.transforms.single().reqId
        store.cancelTransform()
        store.receiveTransformVariant(reqId, 0, "late", null)
        store.finishTransform(reqId, ok = true)
        assertEquals("draft", store.current.text)
        assertNull(store.current.transformError)
    }

    @Test
    fun `a rewrite with no reply times out, each variant resetting the clock`() = runTest {
        val store = composer(link, media)
        store.setText("draft")
        store.startTransform("Improve", variants = 2)
        val reqId = link.transforms.single().reqId
        settle(170_000)
        store.receiveTransformVariant(reqId, 0, "a", null)
        settle(170_000)
        assertTrue(store.current.transforming)
        settle(10_001)
        assertFalse(store.current.transforming)
        assertFalse(store.current.showVariants)
        assertEquals("The rewrite timed out. Try again.", store.current.transformError)
    }
}
