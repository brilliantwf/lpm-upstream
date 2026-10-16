package cx.lpm.mobile.feature.notes.attachment

import android.content.ClipData
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Serves an attachment to the share sheet straight from memory, through a pipe — the
 * plaintext never touches the phone's disk on its way there (iOS hands the share
 * sheet in-memory data the same way).
 */
class NoteShareProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? = NoteShares[uri]?.mimeType

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val item = NoteShares[uri] ?: return null
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(columns, 1).apply {
            addRow(
                columns.map {
                    when (it) {
                        OpenableColumns.DISPLAY_NAME -> item.name
                        OpenableColumns.SIZE -> item.data.size.toLong()
                        else -> null
                    }
                }
            )
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val item = NoteShares[uri] ?: throw FileNotFoundException(uri.toString())
        return openPipeHelper(uri, item.mimeType, null, item.data) { output, _, _, _, bytes ->
            try {
                FileOutputStream(output.fileDescriptor).use { it.write(bytes) }
            } catch (_: IOException) {
                // The receiving app closed its end early.
            }
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

/** The attachments offered to the share sheet lately, by the URI each was offered under. */
internal object NoteShares {
    class Item(val name: String, val mimeType: String, val data: ByteArray)

    private val items = LinkedHashMap<String, Item>()

    operator fun get(uri: Uri): Item? = synchronized(items) { items[uri.lastPathSegment] }

    /** Offer [data] under a fresh URI; only the last few offers stay readable. */
    fun offer(context: Context, name: String, mimeType: String, data: ByteArray): Uri {
        val token = UUID.randomUUID().toString()
        synchronized(items) {
            items[token] = Item(name, mimeType.ifEmpty { "application/octet-stream" }, data)
            while (items.size > KEPT) items.remove(items.keys.first())
        }
        return Uri.Builder()
            .scheme("content")
            .authority("${context.packageName}.notes.share")
            .appendPath(token)
            .build()
    }

    private const val KEPT = 4
}

/** Opens the share sheet for an attachment held in memory. */
internal fun Context.shareNoteAttachment(name: String, mimeType: String, data: ByteArray) {
    val uri = NoteShares.offer(this, name, mimeType, data)
    val send = Intent(Intent.ACTION_SEND)
        .setType(mimeType.ifEmpty { "application/octet-stream" })
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TITLE, name)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(name, uri)
    startActivity(Intent.createChooser(send, null))
}
