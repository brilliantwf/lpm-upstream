package cx.lpm.mobile

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.tailnet.BuiltInTailscale
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var session: MacSession

    @Inject
    lateinit var tailscale: BuiltInTailscale

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !viewModel.ready.value }
        viewModel.start()
        enableEdgeToEdge()
        setContent {
            val ready by viewModel.ready.collectAsStateWithLifecycle()
            val appearance by viewModel.appearance.collectAsStateWithLifecycle()
            val dark = appearance.isDark(isSystemInDarkTheme())
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            LpmTheme(darkTheme = dark) {
                if (ready) LpmApp(session, tailscale.state)
            }
        }
    }
}
