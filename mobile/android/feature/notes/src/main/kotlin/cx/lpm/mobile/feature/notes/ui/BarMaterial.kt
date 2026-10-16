package cx.lpm.mobile.feature.notes.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** SwiftUI's `.bar` material as it reads over a plain list. */
@Composable
@ReadOnlyComposable
internal fun barMaterial(): Color =
    if (LpmTheme.colors.isDark) Color(BAR_DARK) else Color(BAR_LIGHT)

private const val BAR_LIGHT = 0xFFF7F7F7
private const val BAR_DARK = 0xFF161617
