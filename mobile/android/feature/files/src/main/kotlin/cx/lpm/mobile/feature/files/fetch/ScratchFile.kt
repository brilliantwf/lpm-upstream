package cx.lpm.mobile.feature.files.fetch

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Where previews copy files to: a folder per fetch under [root], touched only on
 * [disk] — one task at a time, in the order asked — so ranges land in order and a
 * folder goes away only after its last write. Previews from a previous run that
 * never got to clean up are removed first.
 */
class ScratchSpace(
    val root: File,
    internal val disk: CoroutineDispatcher,
    internal val scope: CoroutineScope
) {
    init {
        scope.launch(disk) { root.deleteRecursively() }
    }

    internal fun create(folder: String, name: String, onFailure: () -> Unit): ScratchFile =
        ScratchFile(File(File(root, folder), name), this, onFailure)
}

/**
 * One fetch's copy of a file on the Mac, written range by range at each range's own
 * offset. A write that fails calls `onFailure` on the main thread; nothing runs
 * once the file is discarded.
 */
internal class ScratchFile(
    val file: File,
    private val space: ScratchSpace,
    private val onFailure: () -> Unit
) {
    // Only used on the disk dispatcher.
    private var out: RandomAccessFile? = null

    @Volatile private var discarded = false

    init {
        onDisk {
            file.parentFile?.mkdirs()
            out = RandomAccessFile(file, "rw").apply { setLength(0) }
        }
    }

    fun write(offset: Long, data: ByteArray) = onDisk {
        val out = out ?: throw IOException("The scratch file isn't open.")
        out.seek(offset)
        out.write(data)
    }

    /** Cut the file to [length] and close it, then hand it over. */
    fun finish(length: Long, then: (File) -> Unit) = onDisk(then = { then(file) }) {
        out?.run {
            setLength(length)
            close()
        }
        out = null
    }

    fun discard() {
        discarded = true
        space.scope.launch(space.disk) {
            runCatching { out?.close() }
            out = null
            file.parentFile?.deleteRecursively()
        }
    }

    private fun onDisk(then: () -> Unit = {}, work: () -> Unit) {
        space.scope.launch {
            val ok = withContext(space.disk) {
                if (discarded) return@withContext false
                try {
                    work()
                    true
                } catch (_: IOException) {
                    false
                }
            }
            if (discarded) return@launch
            if (ok) then() else onFailure()
        }
    }
}
