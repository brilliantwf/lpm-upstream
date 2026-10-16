package cx.lpm.mobile.feature.settings

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import cx.lpm.mobile.core.navigation.Navigator
import cx.lpm.mobile.core.navigation.SettingsKey
import cx.lpm.mobile.core.navigation.SheetPresentation

/** Settings, a sheet over the projects list; "Done" or a swipe down closes it. */
fun EntryProviderScope<NavKey>.settingsEntries(
    navigator: Navigator,
    slots: SettingsSlots = SettingsSlots()
) {
    entry<SettingsKey>(metadata = SheetPresentation.metadata) {
        SettingsSheet(onDone = navigator::back, slots = slots)
    }
}
