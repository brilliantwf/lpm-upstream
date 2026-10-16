package cx.lpm.mobile.feature.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.lpm.mobile.core.data.link.MacBrowser
import cx.lpm.mobile.core.designsystem.R as DesignR
import cx.lpm.mobile.core.designsystem.component.ActivityIndicator
import cx.lpm.mobile.core.designsystem.component.ActivityIndicatorSize
import cx.lpm.mobile.core.designsystem.component.CapsuleButton
import cx.lpm.mobile.core.designsystem.component.DisclosureChevron
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/**
 * Macs found on this Wi-Fi, first on the pairing screen: tap one, then approve on
 * the Mac. Says honestly when it's still looking, when nothing turned up, and when
 * local network access is off. A Mac already on this phone shows as added — except
 * the one being paired again, which stays tappable.
 */
@Composable
internal fun NearbyMacs(
    state: PairingUiState,
    onPick: (MacBrowser.DiscoveredMac) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LpmTheme.colors
    val macs = state.nearby
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.nearby_header).uppercase(),
            style = LpmTheme.typography.footnote.copy(fontWeight = FontWeight.SemiBold),
            color = colors.secondaryLabel,
            modifier = Modifier.padding(start = 4.dp)
        )
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.secondarySystemGroupedBackground, RoundedCornerShape(18.dp))
        ) {
            when {
                state.browse.denied -> StateRow(
                    icon = DesignR.drawable.ic_shield_lock,
                    title = stringResource(R.string.nearby_denied_title),
                    detail = stringResource(R.string.nearby_denied_detail)
                ) {
                    CapsuleButton(
                        stringResource(R.string.settings),
                        onOpenSettings,
                        textStyle = LpmTheme.typography.subheadline
                    )
                }

                macs.isEmpty() && state.browse.searchedAWhile -> StateRow(
                    icon = DesignR.drawable.ic_search,
                    title = stringResource(R.string.nearby_none_title),
                    detail = stringResource(R.string.nearby_none_detail)
                ) {
                    ActivityIndicator(size = ActivityIndicatorSize.Small)
                }

                macs.isEmpty() -> StateRow(
                    icon = null,
                    title = stringResource(R.string.nearby_looking),
                    detail = null
                )

                else -> macs.forEachIndexed { index, mac ->
                    val paired = mac.serverId?.let { it in state.pairedServerIds } ?: false
                    val resolving = state.resolvingId == mac.id
                    NearbyMacRow(mac, paired, resolving) { onPick(mac) }
                    if (index < macs.lastIndex) {
                        Box(
                            Modifier
                                .padding(start = 72.dp)
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(colors.separator)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StateRow(
    icon: Int?,
    title: String,
    detail: String?,
    accessory: @Composable () -> Unit = {}
) {
    val colors = LpmTheme.colors
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = colors.secondaryLabel,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                ActivityIndicator()
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = LpmTheme.typography.headline)
            if (detail != null) {
                Text(detail, style = LpmTheme.typography.subheadline, color = colors.secondaryLabel)
            }
        }
        Spacer(Modifier.width(8.dp))
        accessory()
    }
}

@Composable
private fun NearbyMacRow(
    mac: MacBrowser.DiscoveredMac,
    paired: Boolean,
    resolving: Boolean,
    onClick: () -> Unit
) {
    val colors = LpmTheme.colors
    val subtitle = when {
        mac.isDev -> stringResource(R.string.nearby_dev)
        paired -> stringResource(R.string.nearby_added_detail)
        mac.requestPair -> stringResource(R.string.nearby_approve)
        else -> stringResource(R.string.nearby_linux)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !paired, role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(42.dp)
                .alpha(if (paired) 0.4f else 1f)
                .background(
                    if (mac.requestPair) colors.blue else colors.gray,
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(if (mac.requestPair) MacIcon else HostIcon),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                mac.displayName.ifEmpty { stringResource(R.string.mac) },
                style = LpmTheme.typography.headline,
                color = if (paired) colors.secondaryLabel else colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = LpmTheme.typography.subheadline,
                color = colors.secondaryLabel,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(8.dp))
        when {
            resolving -> ActivityIndicator(size = ActivityIndicatorSize.Small)

            paired -> Text(
                stringResource(R.string.nearby_added),
                style = LpmTheme.typography.subheadline,
                color = colors.secondaryLabel
            )

            else -> DisclosureChevron()
        }
    }
}

private val MacIcon = DesignR.drawable.ic_desktop_mac
private val HostIcon = DesignR.drawable.ic_dns
