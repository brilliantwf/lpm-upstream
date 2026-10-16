package cx.lpm.mobile.feature.usage

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import cx.lpm.mobile.core.navigation.Navigator
import cx.lpm.mobile.core.navigation.StatsKey
import cx.lpm.mobile.core.navigation.UsageKey
import cx.lpm.mobile.feature.usage.stats.StatsRoute
import cx.lpm.mobile.feature.usage.usage.UsageRoute

/** The Stats and Usage screens. */
fun EntryProviderScope<NavKey>.usageEntries(navigator: Navigator) {
    entry<StatsKey> { key -> StatsRoute(key = key, onBack = navigator::back) }
    entry<UsageKey> { key -> UsageRoute(key = key, onBack = navigator::back) }
}
