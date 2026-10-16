package cx.lpm.mobile.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * iOS 26's semantic colors and system palette, one set per appearance. Names follow
 * UIKit (`secondaryLabel`, `tertiarySystemFill`, `systemBlue` as [blue]…) so a Swift
 * view's colors port one to one.
 */
@Immutable
data class LpmColors(
    val systemBackground: Color,
    val secondarySystemBackground: Color,
    val tertiarySystemBackground: Color,
    val systemGroupedBackground: Color,
    val secondarySystemGroupedBackground: Color,
    val tertiarySystemGroupedBackground: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val quaternaryLabel: Color,
    val placeholderText: Color,
    val systemFill: Color,
    val secondarySystemFill: Color,
    val tertiarySystemFill: Color,
    val quaternarySystemFill: Color,
    /** `UIColor.separator`: SwiftUI's `Divider`, a hairline. */
    val separator: Color,
    val opaqueSeparator: Color,
    /** The 1 pt line iOS 26 draws between the rows of an inset-grouped list. */
    val listSeparator: Color,
    val link: Color,
    val red: Color,
    val orange: Color,
    val yellow: Color,
    val green: Color,
    val mint: Color,
    val teal: Color,
    val cyan: Color,
    val blue: Color,
    val indigo: Color,
    val purple: Color,
    val pink: Color,
    val brown: Color,
    val gray: Color,
    val gray2: Color,
    val gray3: Color,
    val gray4: Color,
    val gray5: Color,
    val gray6: Color,
    /** Off track of a switch, which iOS 26 draws darker than `systemFill`. */
    val switchTrackOff: Color,
    /** Liquid Glass controls: the wash over whatever sits behind them. */
    val glassFill: Color,
    /** Rim of a glass control: lit at the top and bottom edges, shaded at the sides. */
    val glassRimLight: Color,
    val glassRimShade: Color,
    val glassShadow: Color,
    /** A context menu's frosted panel. */
    val menuBackground: Color,
    val menuShadow: Color,
    /** Dims what sits behind a sheet. */
    val scrim: Color,
    val isDark: Boolean
) {
    /** The app tint: SwiftUI's `accentColor`, iOS 26's system blue. */
    val tint: Color get() = blue
}

val LightLpmColors = LpmColors(
    systemBackground = Color(0xFFFFFFFF),
    secondarySystemBackground = Color(0xFFF2F2F7),
    tertiarySystemBackground = Color(0xFFFFFFFF),
    systemGroupedBackground = Color(0xFFF2F2F7),
    secondarySystemGroupedBackground = Color(0xFFFFFFFF),
    tertiarySystemGroupedBackground = Color(0xFFF2F2F7),
    label = Color(0xFF000000),
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    quaternaryLabel = Color(0x2E3C3C43),
    placeholderText = Color(0x4D3C3C43),
    systemFill = Color(0x33787880),
    secondarySystemFill = Color(0x29787880),
    tertiarySystemFill = Color(0x1F767680),
    quaternarySystemFill = Color(0x14747480),
    separator = Color(0x4A3C3C43),
    opaqueSeparator = Color(0xFFC6C6C8),
    listSeparator = Color(0xFFE8E8E8),
    link = Color(0xFF007AFF),
    red = Color(0xFFFF383C),
    orange = Color(0xFFFF8D28),
    yellow = Color(0xFFFFCC00),
    green = Color(0xFF34C759),
    mint = Color(0xFF00C8B3),
    teal = Color(0xFF00C3D0),
    cyan = Color(0xFF00C0E8),
    blue = Color(0xFF0088FF),
    indigo = Color(0xFF6155F5),
    purple = Color(0xFFCB30E0),
    pink = Color(0xFFFF2D55),
    brown = Color(0xFFAC7F5E),
    gray = Color(0xFF8E8E93),
    gray2 = Color(0xFFAEAEB2),
    gray3 = Color(0xFFC7C7CC),
    gray4 = Color(0xFFD1D1D6),
    gray5 = Color(0xFFE5E5EA),
    gray6 = Color(0xFFF2F2F7),
    switchTrackOff = Color(0x6E787880),
    glassFill = Color(0x66FFFFFF),
    glassRimLight = Color(0xFFFFFFFF),
    glassRimShade = Color(0x5C000000),
    glassShadow = Color(0x0F000000),
    menuBackground = Color(0xFCF9F9FC),
    menuShadow = Color(0x24000000),
    scrim = Color(0x33000000),
    isDark = false
)

val DarkLpmColors = LpmColors(
    systemBackground = Color(0xFF000000),
    secondarySystemBackground = Color(0xFF1C1C1E),
    tertiarySystemBackground = Color(0xFF2C2C2E),
    systemGroupedBackground = Color(0xFF000000),
    secondarySystemGroupedBackground = Color(0xFF1C1C1E),
    tertiarySystemGroupedBackground = Color(0xFF2C2C2E),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    quaternaryLabel = Color(0x29EBEBF5),
    placeholderText = Color(0x4DEBEBF5),
    systemFill = Color(0x5C787880),
    secondarySystemFill = Color(0x52787880),
    tertiarySystemFill = Color(0x3D767680),
    quaternarySystemFill = Color(0x2E767680),
    separator = Color(0x99545458),
    opaqueSeparator = Color(0xFF38383A),
    listSeparator = Color(0xFF38383A),
    link = Color(0xFF0984FF),
    red = Color(0xFFFF4245),
    orange = Color(0xFFFF9230),
    yellow = Color(0xFFFFD600),
    green = Color(0xFF30D158),
    mint = Color(0xFF00DAC3),
    teal = Color(0xFF00D2E0),
    cyan = Color(0xFF3CD3FE),
    blue = Color(0xFF0091FF),
    indigo = Color(0xFF6D7CFF),
    purple = Color(0xFFDB34F2),
    pink = Color(0xFFFF375F),
    brown = Color(0xFFB78A66),
    gray = Color(0xFF8E8E93),
    gray2 = Color(0xFF636366),
    gray3 = Color(0xFF48484A),
    gray4 = Color(0xFF3A3A3C),
    gray5 = Color(0xFF2C2C2E),
    gray6 = Color(0xFF1C1C1E),
    switchTrackOff = Color(0x66787880),
    glassFill = Color(0x24FFFFFF),
    glassRimLight = Color(0x66FFFFFF),
    glassRimShade = Color(0xCC000000),
    glassShadow = Color(0x33000000),
    menuBackground = Color(0xFC252527),
    menuShadow = Color(0x52000000),
    scrim = Color(0x66000000),
    isDark = true
)

/**
 * The backgrounds iOS uses in an elevated context (sheets, popovers): the same in
 * light mode, one step lighter in dark mode.
 */
internal fun LpmColors.elevated(): LpmColors = if (!isDark) {
    this
} else {
    copy(
        systemBackground = Color(0xFF1C1C1E),
        secondarySystemBackground = Color(0xFF2C2C2E),
        tertiarySystemBackground = Color(0xFF3A3A3C),
        systemGroupedBackground = Color(0xFF1C1C1E),
        secondarySystemGroupedBackground = Color(0xFF2C2C2E),
        tertiarySystemGroupedBackground = Color(0xFF3A3A3C)
    )
}
