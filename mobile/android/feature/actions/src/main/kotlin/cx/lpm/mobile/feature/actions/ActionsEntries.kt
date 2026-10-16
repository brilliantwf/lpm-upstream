package cx.lpm.mobile.feature.actions

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import cx.lpm.mobile.core.navigation.Navigator
import cx.lpm.mobile.core.navigation.ProjectActionsKey
import cx.lpm.mobile.core.navigation.SheetPresentation
import cx.lpm.mobile.feature.actions.sheet.ProjectActionsRoute

/** The ⚡ sheet, over the project screen it was opened from, at the medium detent. */
fun EntryProviderScope<NavKey>.actionsEntries(navigator: Navigator) {
    entry<ProjectActionsKey>(metadata = SheetPresentation.mediumMetadata) { key ->
        ProjectActionsRoute(key = key, onDismiss = navigator::back)
    }
}
