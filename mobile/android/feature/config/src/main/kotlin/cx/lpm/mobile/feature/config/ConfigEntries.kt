package cx.lpm.mobile.feature.config

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import cx.lpm.mobile.core.navigation.AddProjectKey
import cx.lpm.mobile.core.navigation.Navigator
import cx.lpm.mobile.core.navigation.ProjectConfigKey
import cx.lpm.mobile.core.navigation.SheetPresentation
import cx.lpm.mobile.core.navigation.YamlConfigKey
import cx.lpm.mobile.feature.config.add.AddProjectRoute
import cx.lpm.mobile.feature.config.hub.ProjectConfigRoute
import cx.lpm.mobile.feature.config.yaml.YamlConfigRoute

/**
 * "Configure…", "Edit Configuration File" and "Add Project…", each a sheet over the
 * screen it was opened from.
 */
fun EntryProviderScope<NavKey>.configEntries(
    navigator: Navigator,
    slots: ConfigSlots = ConfigSlots()
) {
    entry<ProjectConfigKey>(metadata = SheetPresentation.metadata) { key ->
        ProjectConfigRoute(key = key, onDone = navigator::back, slots = slots)
    }
    entry<YamlConfigKey>(metadata = SheetPresentation.metadata) { key ->
        YamlConfigRoute(key = key, onDismiss = navigator::back)
    }
    entry<AddProjectKey>(metadata = SheetPresentation.metadata) {
        AddProjectRoute(onDismiss = navigator::back)
    }
}
