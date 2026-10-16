package cx.lpm.mobile.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

private val LocalLpmColors = staticCompositionLocalOf { LightLpmColors }
private val DefaultTypography = LpmTypography()
private val LocalLpmTypography = staticCompositionLocalOf { DefaultTypography }

// The outermost theme's appearance: the app's own, under any screen that forces one.
private val LocalAppDarkTheme = staticCompositionLocalOf<Boolean?> { null }

/**
 * The app theme: iOS 26 colors and text styles, also mapped onto Material 3 so stock
 * components blend in. There is no dynamic color: the app looks the same on every
 * phone, as on iOS.
 */
@Composable
fun LpmTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkLpmColors else LightLpmColors
    val typography = DefaultTypography
    CompositionLocalProvider(
        LocalLpmColors provides colors,
        LocalLpmTypography provides typography,
        LocalAppDarkTheme provides (LocalAppDarkTheme.current ?: darkTheme)
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = typography.toMaterial()
        ) {
            CompositionLocalProvider(LocalContentColor provides colors.label, content = content)
        }
    }
}

/**
 * [content] in the app's own appearance, for a sheet over a screen that forces its
 * own (the terminal is always dark; a sheet that sets no color scheme isn't, on iOS).
 */
@Composable
fun AppAppearance(content: @Composable () -> Unit) {
    LpmTheme(darkTheme = LocalAppDarkTheme.current ?: isSystemInDarkTheme(), content = content)
}

/** Gives [content] iOS's elevated backgrounds, as inside a sheet. */
@Composable
internal fun ElevatedColors(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLpmColors provides LpmTheme.colors.elevated(), content = content)
}

object LpmTheme {
    val colors: LpmColors
        @Composable @ReadOnlyComposable
        get() = LocalLpmColors.current

    val typography: LpmTypography
        @Composable @ReadOnlyComposable
        get() = LocalLpmTypography.current
}

private fun LpmColors.toMaterial(): ColorScheme {
    val surface = secondarySystemGroupedBackground
    val onSurfaceVariant = secondaryLabel.compositeOver(surface)
    val outline = separator.compositeOver(surface)
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = blue,
        onPrimary = Color.White,
        primaryContainer = tertiarySystemFill.compositeOver(surface),
        onPrimaryContainer = blue,
        inversePrimary = blue,
        secondary = gray,
        onSecondary = Color.White,
        secondaryContainer = tertiarySystemFill.compositeOver(surface),
        onSecondaryContainer = label,
        tertiary = indigo,
        onTertiary = Color.White,
        tertiaryContainer = tertiarySystemFill.compositeOver(surface),
        onTertiaryContainer = indigo,
        background = systemGroupedBackground,
        onBackground = label,
        surface = surface,
        onSurface = label,
        surfaceVariant = tertiarySystemFill.compositeOver(surface),
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = Color.Transparent,
        inverseSurface = label,
        inverseOnSurface = systemBackground,
        error = red,
        onError = Color.White,
        errorContainer = red.copy(alpha = 0.15f).compositeOver(surface),
        onErrorContainer = red,
        outline = outline,
        outlineVariant = listSeparator,
        scrim = Color.Black,
        surfaceBright = surface,
        surfaceDim = systemGroupedBackground,
        surfaceContainerLowest = systemBackground,
        surfaceContainerLow = systemGroupedBackground,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = tertiarySystemGroupedBackground
    )
}
