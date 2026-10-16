package cx.lpm.mobile.feature.files.preview

import androidx.compose.ui.graphics.Color
import cx.lpm.mobile.core.designsystem.theme.LpmColors

/**
 * SwiftUI's `.bar` material as it shows on the sheet: behind the Markdown switch,
 * reaching up behind the navigation bar, and behind the "first 1 MB" note.
 */
internal val LpmColors.barMaterial: Color
    get() = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF7F8F7)
