package cx.lpm.mobile

import android.content.Intent
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
import cx.lpm.mobile.core.data.push.NotificationOpenTarget
import cx.lpm.mobile.core.data.push.NotificationOpens
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.designsystem.theme.LpmTheme
import cx.lpm.mobile.core.push.PushNotifier
import cx.lpm.mobile.core.push.PushStartup
import cx.lpm.mobile.core.tailnet.BuiltInTailscale
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var session: MacSession

    @Inject
    lateinit var tailscale: BuiltInTailscale

    @Inject
    lateinit var opens: NotificationOpens

    @Inject
    lateinit var push: PushStartup

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !viewModel.ready.value }
        viewModel.start()
        // A recreated activity was already handed this intent.
        if (savedInstanceState == null) openNotification(intent)
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
                if (ready) LpmApp(session, tailscale.state, opens)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openNotification(intent)
    }

    override fun onResume() {
        super.onResume()
        push.resumed()
    }

    private fun openNotification(intent: Intent) {
        if (intent.action != PushNotifier.ACTION_OPEN) return
        NotificationOpenTarget.from(intent::getStringExtra)?.let(opens::open)
    }
}
