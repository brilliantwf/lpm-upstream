package cx.lpm.mobile.feature.usage.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import cx.lpm.mobile.core.designsystem.theme.LpmTheme

/** How a provider is named and colored (the desktop's `providerMeta` in statsDerive.ts). */
@Immutable
data class ProviderMeta(val label: String, val short: String, val color: Color?)

private val ClaudeColor = Color(0xFFD97757)
private val CodexColor = Color(0xFF10A37F)

/** Display metadata for a provider key; unknown keys fall back to the raw key, muted. */
fun providerMeta(key: String): ProviderMeta = when (key) {
    "claude" -> ProviderMeta("Claude Code", "Claude", ClaudeColor)
    "codex" -> ProviderMeta("Codex", "Codex", CodexColor)
    else -> ProviderMeta(key, key, null)
}

/** A provider's dot and bar color; an unknown one is drawn in the secondary label color. */
val ProviderMeta.tint: Color
    @Composable @ReadOnlyComposable
    get() = color ?: LpmTheme.colors.secondaryLabel

/** The colors the usage meters and tags are drawn in, per appearance. */
@Immutable
data class UsagePalette(
    /** The one tint usage bars are drawn in however full they get: the desktop's `--accent-cyan`. */
    val fill: Color,
    /** The use past a bar's pace tick: the desktop's `--accent-red`. */
    val ahead: Color,
    /** The "in use" tag's text and wash. */
    val inUseText: Color,
    val inUseWash: Color
)

private val LightUsage = UsagePalette(
    fill = Color(0xFF06B6D4),
    ahead = Color(0xFFEF4444),
    inUseText = Color(0xFFAE5630),
    inUseWash = Color(0xFFD97757).copy(alpha = 0.16f)
)

private val DarkUsage = UsagePalette(
    fill = Color(0xFF22D3EE),
    ahead = Color(0xFFF87171),
    inUseText = Color(0xFFDE8A68),
    inUseWash = Color(0xFFDE8A68).copy(alpha = 0.16f)
)

val usagePalette: UsagePalette
    @Composable @ReadOnlyComposable
    get() = if (LpmTheme.colors.isDark) DarkUsage else LightUsage
