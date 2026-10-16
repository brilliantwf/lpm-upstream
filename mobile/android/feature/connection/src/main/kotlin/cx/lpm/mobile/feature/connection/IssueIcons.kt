package cx.lpm.mobile.feature.connection

import androidx.annotation.DrawableRes
import cx.lpm.mobile.core.data.link.AddressKind
import cx.lpm.mobile.core.designsystem.R

/** The Material Symbol drawn for an issue's SF Symbol name (`IssueCopy.icon`). */
@DrawableRes
internal fun issueIcon(symbol: String): Int = when (symbol) {
    "arrow.triangle.2.circlepath" -> R.drawable.ic_sync
    "wifi.slash" -> R.drawable.ic_wifi_off
    "moon.zzz" -> R.drawable.ic_bedtime
    "moon.fill" -> R.drawable.ic_bedtime_fill
    "network" -> R.drawable.ic_language
    "app.dashed" -> R.drawable.ic_check_box_outline_blank
    "lock.trianglebadge.exclamationmark" -> R.drawable.ic_lock
    "macbook.and.iphone" -> R.drawable.ic_devices
    "exclamationmark.shield" -> R.drawable.ic_gpp_maybe
    "xmark.shield" -> R.drawable.ic_gpp_bad
    "wifi.exclamationmark" -> R.drawable.ic_signal_wifi_bad
    "key.slash" -> R.drawable.ic_key_off
    "qrcode" -> R.drawable.ic_qr_code
    "point.3.connected.trianglepath.dotted" -> R.drawable.ic_device_hub
    "arrow.up.circle" -> R.drawable.ic_arrow_circle_up
    "iphone.slash" -> R.drawable.ic_mobile_off
    else -> R.drawable.ic_warning
}

/** What each kind of address looks like in a list (`AddressKind.symbol`). */
@DrawableRes
internal fun AddressKind.icon(): Int = when (this) {
    AddressKind.Tailscale -> R.drawable.ic_language
    AddressKind.Home -> R.drawable.ic_wifi
    AddressKind.LocalName -> R.drawable.ic_home
    AddressKind.ThisDevice -> R.drawable.ic_smartphone
    AddressKind.Internet -> R.drawable.ic_public
}

/** Tailscale first: it's the address that works from anywhere. */
internal val AddressKind.sortRank: Int
    get() = when (this) {
        AddressKind.Tailscale -> 0
        AddressKind.Internet -> 1
        AddressKind.Home -> 2
        AddressKind.LocalName -> 3
        AddressKind.ThisDevice -> 4
    }
