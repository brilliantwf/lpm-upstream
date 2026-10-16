package cx.lpm.mobile.feature.terminal.composer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/** [ComposerMedia] over the content resolver, the bitmap codecs and a cache folder. */
@Singleton
class AndroidComposerMedia @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ComposerMedia {
    private val resolver get() = context.contentResolver

    /** Emptied on first use each launch: anything still here was left by a run that ended mid-upload. */
    private val root: File by lazy {
        File(context.cacheDir, SCRATCH_FOLDER).apply { deleteRecursively() }
    }

    override fun isVideo(source: PickedUri): Boolean =
        resolver.getType(source.value.toUri())?.startsWith("video/") == true

    override fun displayName(source: PickedUri): String {
        val uri = source.value.toUri()
        val name = runCatching {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        return name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "file"
    }

    override fun encodeImage(source: PickedUri): EncodedImage? {
        val image = decode(source, UPLOAD_MAX_DIMENSION) ?: return null
        val jpeg = ByteArrayOutputStream()
        if (!image.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, jpeg)) return null
        return EncodedImage(
            b64 = Base64.getEncoder().encodeToString(jpeg.toByteArray()),
            thumbnail = image.fitting(THUMBNAIL_MAX_DIMENSION).asImageBitmap()
        )
    }

    override fun copyToScratch(source: PickedUri): File? = try {
        val dir = File(root, UUID.randomUUID().toString()).apply { mkdirs() }
        val dest = File(dir, displayName(source).replace('/', '_'))
        resolver.openInputStream(source.value.toUri())?.use { input ->
            dest.outputStream().use { input.copyTo(it) }
        } ?: return null
        dest
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }

    override fun mime(file: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: "application/octet-stream"

    override fun videoThumbnail(file: File): ImageBitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.path)
            retriever.getFrameAtTime(0)?.fitting(THUMBNAIL_MAX_DIMENSION)?.asImageBitmap()
        } catch (_: RuntimeException) {
            null
        } finally {
            retriever.release()
        }
    }

    override fun size(file: File): Long? = file.takeIf { it.isFile }?.length()

    override fun readPiece(file: File, offset: Long, length: Long): ByteArray? = try {
        RandomAccessFile(file, "r").use { handle ->
            handle.seek(offset)
            ByteArray(length.toInt()).also(handle::readFully)
        }
    } catch (_: IOException) {
        null
    }

    override fun readAll(file: File): ByteArray? = try {
        file.readBytes()
    } catch (_: IOException) {
        null
    }

    override fun discard(file: File) {
        file.parentFile?.deleteRecursively()
    }

    /** Decoded upright and no larger than `maxDimension` on its longest side. */
    private fun decode(source: PickedUri, maxDimension: Int): Bitmap? {
        val uri = source.value.toUri()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) {
                        decoder,
                        info,
                        _
                    ->
                    val longest = max(info.size.width, info.size.height)
                    if (longest > maxDimension) {
                        val scale = maxDimension.toFloat() / longest
                        decoder.setTargetSize(
                            (info.size.width * scale).roundToInt(),
                            (info.size.height * scale).roundToInt()
                        )
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                resolver.openInputStream(
                    uri
                )?.use(BitmapFactory::decodeStream)?.fitting(maxDimension)
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
        const val SCRATCH_FOLDER = "ComposerUploads"
        const val UPLOAD_MAX_DIMENSION = 2048
        const val THUMBNAIL_MAX_DIMENSION = 240
        const val JPEG_QUALITY = 70
    }
}
