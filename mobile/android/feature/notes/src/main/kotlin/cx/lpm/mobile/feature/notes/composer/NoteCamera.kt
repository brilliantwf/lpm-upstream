package cx.lpm.mobile.feature.notes.composer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import cx.lpm.mobile.feature.notes.R
import java.io.File
import java.util.UUID

/** The camera's output files; a class of its own so it doesn't clash with other providers. */
class NoteCameraFiles : FileProvider(R.xml.notes_camera_paths)

/**
 * Takes a photo with the system camera into a cache file and hands over its URI; null
 * when the device has no camera. Asks for the camera permission first when the app
 * declares it (the system camera then requires it).
 */
@Composable
internal fun rememberNoteCamera(onPhoto: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pending
        pending = null
        if (saved && uri != null) onPhoto(uri)
    }
    val capture = {
        val uri = newPhotoUri(context)
        pending = uri.toString()
        take.launch(uri)
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) capture()
        }
    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) return null
    return {
        if (context.declaresCamera && !context.hasCameraPermission) {
            permission.launch(Manifest.permission.CAMERA)
        } else {
            capture()
        }
    }
}

private fun newPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "NotesCamera").apply { mkdirs() }
    val file = File(dir, "Photo-${UUID.randomUUID()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.notes.camera", file)
}

private val Context.declaresCamera: Boolean
    get() = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        .requestedPermissions?.contains(Manifest.permission.CAMERA) == true

private val Context.hasCameraPermission: Boolean
    get() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
