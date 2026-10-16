package cx.lpm.mobile.feature.notes.chat

import cx.lpm.mobile.feature.notes.composer.EncodedNoteImage
import cx.lpm.mobile.feature.notes.composer.NoteFileRead
import cx.lpm.mobile.feature.notes.composer.NoteMedia
import kotlinx.coroutines.CompletableDeferred

/** Picks by URI: [files] read as given, [images] encode once [release] completes them. */
class FakeNoteMedia : NoteMedia {
    val files = mutableMapOf<String, NoteFileRead>()
    val images = mutableMapOf<String, CompletableDeferred<EncodedNoteImage?>>()
    val discarded = mutableListOf<String>()

    fun release(uri: String, bytes: ByteArray?) {
        images.getOrPut(uri) {
            CompletableDeferred()
        }.complete(bytes?.let { EncodedNoteImage(it, null) })
    }

    override suspend fun displayName(uri: String): String = uri.substringAfterLast('/')

    override suspend fun mimeType(uri: String): String = "text/plain"

    override suspend fun encodeImage(uri: String): EncodedNoteImage? =
        images.getOrPut(uri) { CompletableDeferred() }.await()

    override suspend fun readFile(uri: String, limit: Int): NoteFileRead =
        files[uri] ?: NoteFileRead.Unreadable

    override suspend fun discardCapture(uri: String) {
        discarded += uri
    }
}
