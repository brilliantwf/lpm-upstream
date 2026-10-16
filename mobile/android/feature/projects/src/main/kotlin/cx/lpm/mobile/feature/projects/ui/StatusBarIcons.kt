package cx.lpm.mobile.feature.projects.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * White status bar icons while [light] is on — over a colored header such as the
 * purple Demo Mode banner — and the app's own again once it goes.
 */
@Composable
fun LightStatusBarIcons(light: Boolean) {
    val view = LocalView.current
    DisposableEffect(light, view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        if (light) controller?.isAppearanceLightStatusBars = false
        onDispose {
            if (light && before != null) controller.isAppearanceLightStatusBars = before
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
