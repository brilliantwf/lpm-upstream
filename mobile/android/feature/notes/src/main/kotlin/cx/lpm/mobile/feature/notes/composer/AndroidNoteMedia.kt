package cx.lpm.mobile.feature.notes.composer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [NoteMedia] over the content resolver and the bitmap codecs. */
@Singleton
class AndroidNoteMedia @Inject constructor(@param:ApplicationContext private val context: Context) :
    NoteMedia {
    private val resolver get() = context.contentResolver

    override suspend fun displayName(uri: String): String = withContext(Dispatchers.IO) {
        val parsed = uri.toUri()
        val name = runCatching {
            resolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
        name?.takeIf { it.isNotBlank() } ?: parsed.lastPathSegment ?: "file"
    }

    override suspend fun mimeType(uri: String): String = withContext(Dispatchers.IO) {
        resolver.getType(uri.toUri())
            ?: MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(displayName(uri).substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
    }

    override suspend fun encodeImage(uri: String): EncodedNoteImage? =
        withContext(Dispatchers.Default) {
            val image = decode(uri) ?: return@withContext null
            val jpeg = ByteArrayOutputStream()
            if (!image.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, jpeg)) {
                return@withContext null
            }
            EncodedNoteImage(
                jpeg.toByteArray(),
                image.fitting(THUMBNAIL_MAX_DIMENSION).asImageBitmap()
            )
        }

    override suspend fun readFile(uri: String, limit: Int): NoteFileRead =
        withContext(Dispatchers.IO) {
            try {
                resolver.openInputStream(uri.toUri())?.use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        if (out.size() > limit) return@withContext NoteFileRead.TooLarge
                    }
                    NoteFileRead.Bytes(out.toByteArray())
                } ?: NoteFileRead.Unreadable
            } catch (_: IOException) {
                NoteFileRead.Unreadable
            } catch (_: SecurityException) {
                NoteFileRead.Unreadable
            }
        }

    override suspend fun discardCapture(uri: String) {
        withContext(Dispatchers.IO) { runCatching { resolver.delete(uri.toUri(), null, null) } }
    }

    /** Decoded upright and no larger than the upload size on its longest side. */
    private fun decode(uri: String): Bitmap? {
        val parsed = uri.toUri()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, parsed)) {
                        decoder,
                        info,
                        _
                    ->
                    val longest = max(info.size.width, info.size.height)
                    if (longest > UPLOAD_MAX_DIMENSION) {
                        val ratio = UPLOAD_MAX_DIMENSION.toFloat() / longest
                        decoder.setTargetSize(
                            (info.size.width * ratio).roundToInt(),
                            (info.size.height * ratio).roundToInt()
                        )
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                resolver.openInputStream(parsed)?.use(BitmapFactory::decodeStream)
                    ?.fitting(UPLOAD_MAX_DIMENSION)
            }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    private fun Bitmap.fitting(maxDimension: Int): Bitmap {
        val longest = max(width, height)
        if (longest <= maxDimension) return this
        val ratio = maxDimension.toFloat() / longest
        return scale((width * ratio).roundToInt(), (height * ratio).roundToInt())
    }

    private companion object {
        const val UPLOAD_MAX_DIMENSION = 2048
        const val THUMBNAIL_MAX_DIMENSION = 240
        const val JPEG_QUALITY = 70
        const val BUFFER_SIZE = 64 * 1024
    }
}
