package cx.lpm.mobile.core.web

import android.net.Uri
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MarkdownImageLoaderTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val reads = mutableListOf<Pair<String, List<String>>>()
    private val loader = MarkdownImageLoader(scope).apply {
        project = "storefront"
        path = "docs/README.md"
    }

    @After
    fun tearDown() = scope.cancel()

    private fun image(src: String) = Uri.parse("lpm-file://image?src=" + Uri.encode(src))

    @Test
    fun servesTheFileTheMacSends() {
        val png = temp.newFile("shot.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        loader.reader = MarkdownFileReader { project, candidates ->
            synchronized(reads) { reads += project to candidates }
            png
        }
        val response = loader.intercept(image("img/shot one.png"))!!
        assertEquals(200, response.statusCode)
        assertEquals(listOf(1, 2, 3), response.data.readBytes().map(Byte::toInt))
        assertEquals(listOf("storefront" to listOf("docs/img/shot one.png")), reads)
    }

    @Test
    fun aRootPathTriesTheProjectRootFirst() {
        loader.reader = MarkdownFileReader { _, candidates ->
            synchronized(reads) { reads += "" to candidates }
            null
        }
        assertEquals(404, loader.intercept(image("/assets/logo.svg"))!!.statusCode)
        assertEquals(listOf("assets/logo.svg", "/assets/logo.svg"), reads.single().second)
    }

    @Test
    fun aMissingOrFailedReadIs404SoThePageShowsTheAltText() {
        loader.reader = MarkdownFileReader { _, _ -> null }
        assertEquals(404, loader.intercept(image("a.png"))!!.statusCode)
        loader.reader = MarkdownFileReader { _, _ -> throw IOException("link down") }
        assertEquals(404, loader.intercept(image("a.png"))!!.statusCode)
        loader.reader = MarkdownFileReader { _, _ -> File(temp.root, "gone.png") }
        assertEquals(404, loader.intercept(image("a.png"))!!.statusCode)
        assertEquals(404, loader.intercept(Uri.parse("lpm-file://image"))!!.statusCode)
        assertEquals(404, loader.intercept(image("#top"))!!.statusCode)
    }

    @Test
    fun otherSchemesAreLeftToTheAssetLoader() {
        loader.reader = MarkdownFileReader { _, _ -> error("not called") }
        assertNull(
            loader.intercept(
                Uri.parse("https://appassets.androidplatform.net/assets/web/markdown/markdown.css")
            )
        )
        assertNull(loader.intercept(Uri.parse("https://img.shields.io/badge.svg")))
    }

    @Test
    fun cancelAllEndsPendingReads() {
        val started = CompletableDeferred<Unit>()
        loader.reader = MarkdownFileReader { _, _ ->
            started.complete(Unit)
            CompletableDeferred<File>().await()
        }
        var status = 0
        val request = Thread { status = loader.intercept(image("slow.png"))!!.statusCode }
        request.start()
        while (!started.isCompleted) Thread.sleep(5)
        loader.cancelAll()
        request.join(5_000)
        assertEquals(404, status)
    }
}
