package cx.lpm.mobile.feature.terminal

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import cx.lpm.mobile.core.data.terminals.TerminalEnding
import cx.lpm.mobile.core.designsystem.R as DesignR

/**
 * Over a terminal whose process is gone, so its last output stays readable but
 * nobody keeps typing into it. Styled like the reconnecting banner.
 */
@Composable
internal fun TerminalEndedBanner(ending: TerminalEnding, modifier: Modifier = Modifier) {
    TerminalBannerCapsule(modifier) {
        BannerIcon(painterResource(DesignR.drawable.ic_stop_circle))
        BannerText(ending.message)
    }
}
