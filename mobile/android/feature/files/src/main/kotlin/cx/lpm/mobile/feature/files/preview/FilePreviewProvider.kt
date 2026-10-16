package cx.lpm.mobile.feature.files.preview

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import cx.lpm.mobile.feature.files.R
import cx.lpm.mobile.feature.files.fetch.pathExtension
import java.io.File

/** Lends a previewed file's copy to the app it's shared with (iOS `ShareLink`). */
class FilePreviewProvider : FileProvider(R.xml.file_preview_paths) {
    companion object {
        /** The scratch folder under the cache directory, as `file_preview_paths` names it. */
        const val FOLDER = "FilePreview"

        fun uri(context: Context, file: File): Uri =
            getUriForFile(context, "${context.packageName}.filepreview", file)

        /** The system share sheet for [file], readable by whichever app is picked. */
        fun shareIntent(context: Context, file: File): Intent {
            val uri = uri(context, file)
            val type = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(pathExtension(file.name).lowercase())
                ?: "application/octet-stream"
            val send = Intent(Intent.ACTION_SEND)
                .setType(type)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = ClipData.newRawUri(file.name, uri)
            return Intent.createChooser(send, null)
        }
    }
}
