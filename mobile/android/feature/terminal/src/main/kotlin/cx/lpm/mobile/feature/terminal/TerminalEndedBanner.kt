package cx.lpm.mobile.feature.terminal

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import cx.lpm.mobile.core.designsystem.R as DesignR

/**
 * Over a terminal whose process is gone, so its last output stays readable but
 * nobody keeps typing into it — or over the tab lpm reopened in its place. Styled
 * like the reconnecting banner.
 */
@Composable
internal fun TerminalEndedBanner(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = DesignR.drawable.ic_stop_circle
) {
    TerminalBannerCapsule(modifier) {
        BannerIcon(painterResource(icon))
        BannerText(text)
    }
}
