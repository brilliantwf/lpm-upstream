package cx.lpm.mobile.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * iOS's text styles at the default Dynamic Type size, on the platform font (SF Pro
 * can't ship on Android). Size, weight and line height match iOS (sp = pt); the
 * tracking keeps Roboto's line lengths close to SF Pro's.
 */
@Immutable
class LpmTypography(
    val largeTitle: TextStyle = iosStyle(34, 41, 0.55),
    val title1: TextStyle = iosStyle(28, 34, 0.5),
    val title2: TextStyle = iosStyle(22, 28, -0.1),
    val title3: TextStyle = iosStyle(20, 25, -0.3),
    val headline: TextStyle = iosStyle(17, 22, 0.35, FontWeight.SemiBold),
    val body: TextStyle = iosStyle(17, 22, 0.08),
    val callout: TextStyle = iosStyle(16, 21, 0.12),
    val subheadline: TextStyle = iosStyle(15, 20, 0.18),
    val footnote: TextStyle = iosStyle(13, 18, 0.28),
    val caption1: TextStyle = iosStyle(12, 16, 0.3),
    val caption2: TextStyle = iosStyle(11, 13, 0.35)
)

private fun iosStyle(
    size: Int,
    lineHeight: Int,
    tracking: Double,
    weight: FontWeight = FontWeight.Normal
): TextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None
    )
)

/** The iOS styles under Material's names, so stock Material components read like iOS. */
internal fun LpmTypography.toMaterial(): Typography = Typography(
    displayLarge = largeTitle,
    displayMedium = title1,
    displaySmall = title2,
    headlineLarge = title1,
    headlineMedium = title2,
    headlineSmall = title3,
    titleLarge = title3,
    titleMedium = headline,
    titleSmall = subheadline.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = body,
    bodyMedium = callout,
    bodySmall = footnote,
    labelLarge = body,
    labelMedium = footnote,
    labelSmall = caption2
)
