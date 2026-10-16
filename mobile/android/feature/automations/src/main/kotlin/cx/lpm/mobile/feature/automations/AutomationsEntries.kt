package cx.lpm.mobile.feature.automations

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import cx.lpm.mobile.core.navigation.AutomationKey
import cx.lpm.mobile.core.navigation.AutomationRunKey
import cx.lpm.mobile.core.navigation.AutomationsKey
import cx.lpm.mobile.core.navigation.Navigator
import cx.lpm.mobile.feature.automations.conversation.AutomationConversationRoute
import cx.lpm.mobile.feature.automations.detail.AutomationDetailRoute
import cx.lpm.mobile.feature.automations.list.AutomationsRoute

/** The automations list, one automation's page, and a run's conversation. */
fun EntryProviderScope<NavKey>.automationsEntries(
    navigator: Navigator,
    slots: AutomationsSlots = AutomationsSlots()
) {
    entry<AutomationsKey> { key ->
        AutomationsRoute(key = key, onNavigate = navigator::navigate, onBack = navigator::back)
    }
    entry<AutomationKey> { key ->
        AutomationDetailRoute(key = key, onNavigate = navigator::navigate, onBack = navigator::back)
    }
    entry<AutomationRunKey> { key ->
        AutomationConversationRoute(key = key, onBack = navigator::back, slots = slots)
    }
}
