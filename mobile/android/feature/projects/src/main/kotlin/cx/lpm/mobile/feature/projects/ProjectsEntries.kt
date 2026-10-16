package cx.lpm.mobile.feature.projects

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import cx.lpm.mobile.core.model.SessionKey
import cx.lpm.mobile.core.navigation.Navigator
import cx.lpm.mobile.core.navigation.ProjectKey
import cx.lpm.mobile.core.navigation.ProjectsKey
import cx.lpm.mobile.feature.projects.detail.ProjectDetailRoute
import cx.lpm.mobile.feature.projects.list.ProjectsRoute
import cx.lpm.mobile.feature.projects.visit.MachineVisitRoute

/**
 * The projects list (the root once a Mac is saved) and each project's screen — a
 * machine's project in a visit session of its own.
 */
fun EntryProviderScope<NavKey>.projectsEntries(
    navigator: Navigator,
    slots: ProjectsSlots = ProjectsSlots()
) {
    entry<ProjectsKey> { ProjectsRoute(onNavigate = navigator::navigate, slots = slots) }
    entry<ProjectKey> { key ->
        if (key.session is SessionKey.Visit) {
            MachineVisitRoute(
                key = key,
                onNavigate = navigator::navigate,
                onBack = navigator::back,
                slots = slots
            )
        } else {
            ProjectDetailRoute(
                key = key,
                onNavigate = navigator::navigate,
                onBack = navigator::back,
                slots = slots
            )
        }
    }
}
