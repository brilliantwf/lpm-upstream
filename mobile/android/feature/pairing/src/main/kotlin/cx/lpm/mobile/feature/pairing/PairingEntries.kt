package cx.lpm.mobile.feature.pairing

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import cx.lpm.mobile.core.navigation.EnterCodeKey
import cx.lpm.mobile.core.navigation.Navigator
import cx.lpm.mobile.core.navigation.PairingKey

/** The pairing root and Enter a Code. [issueCard] is the connection feature's card. */
fun EntryProviderScope<NavKey>.pairingEntries(navigator: Navigator, issueCard: IssueCardSlot) {
    entry<PairingKey> {
        PairingRoute(
            issueCard = issueCard,
            onEnterCode = { navigator.navigate(EnterCodeKey(it.host, it.port)) }
        )
    }
    entry<EnterCodeKey> { key ->
        EnterCodeRoute(
            host = key.host,
            port = key.port,
            onBack = navigator::back,
            issueCard = issueCard,
            insets = WindowInsets.statusBars
        )
    }
}
