package cx.lpm.mobile.feature.activity

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import cx.lpm.mobile.core.navigation.ActivityKey
import cx.lpm.mobile.core.navigation.Navigator

/**
 * Activity, pushed from the projects "…" menu. [connectionStatusBar] is the
 * connection feature's bar for a problem this phone can fix.
 */
fun EntryProviderScope<NavKey>.activityEntries(
    navigator: Navigator,
    connectionStatusBar: @Composable () -> Unit = {}
) {
    entry<ActivityKey> {
        ActivityRoute(
            onNavigate = navigator::navigate,
            onBack = navigator::back,
            connectionStatusBar = connectionStatusBar
        )
    }
}
