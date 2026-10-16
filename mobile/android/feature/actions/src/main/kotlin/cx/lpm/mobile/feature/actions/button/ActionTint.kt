package cx.lpm.mobile.feature.actions.button

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * An action's `color:` as the desktop paints it: a named accent, optionally `-deep`
 * (mixed toward black), that follows light and dark mode, or a raw `#rrggbb`. Buttons
 * draw the label in [text] over glass washed with [glass]; [accent] is the swatch.
 */
@Immutable
class ActionTint private constructor(
    private val light: Color,
    private val dark: Color,
    private val lightText: Color,
    private val darkText: Color
) {
    fun accent(isDark: Boolean): Color = if (isDark) dark else light

    /** A fainter wash on light backgrounds than on dark. */
    fun glass(isDark: Boolean): Color =
        if (isDark) dark.copy(alpha = DARK_GLASS) else light.copy(alpha = LIGHT_GLASS)

    fun text(isDark: Boolean): Color = if (isDark) darkText else lightText

    companion object {
        /** The named colors in the desktop picker's order: a hue wheel, then neutrals. */
        val Names = listOf(
            "red", "orange", "claude", "amber", "yellow", "lime", "green", "emerald", "teal",
            "cyan", "sky", "blue", "indigo", "violet", "purple", "fuchsia", "pink", "rose",
            "slate", "gray", "stone"
        )

        private const val DEEP = "-deep"
        private const val DEEP_SHADE = 0.74f
        private const val LIGHT_GLASS = 0.10f
        private const val DARK_GLASS = 0.22f

        /**
         * The desktop theme's light `--accent-<name>`, light `--accent-<name>-text` and
         * dark `--accent-<name>`; in dark mode the text is the accent itself.
         */
        private val Palette: Map<String, Triple<Long, Long, Long>> = mapOf(
            "red" to Triple(0xef4444, 0xb91c1c, 0xf87171),
            "orange" to Triple(0xf97316, 0xc2410c, 0xfb923c),
            "claude" to Triple(0xd97757, 0xae5630, 0xde8a68),
            "amber" to Triple(0xf59e0b, 0xb45309, 0xfbbf24),
            "yellow" to Triple(0xeab308, 0x854d0e, 0xfacc15),
            "lime" to Triple(0x84cc16, 0x3f6212, 0xa3e635),
            "green" to Triple(0x22c55e, 0x166534, 0x4ade80),
            "emerald" to Triple(0x10b981, 0x047857, 0x34d399),
            "teal" to Triple(0x14b8a6, 0x0f766e, 0x2dd4bf),
            "cyan" to Triple(0x06b6d4, 0x155e75, 0x22d3ee),
            "sky" to Triple(0x0ea5e9, 0x0369a1, 0x38bdf8),
            "blue" to Triple(0x3b82f6, 0x1d4ed8, 0x60a5fa),
            "indigo" to Triple(0x6366f1, 0x4338ca, 0x818cf8),
            "violet" to Triple(0x8b5cf6, 0x6d28d9, 0xa78bfa),
            "purple" to Triple(0xa855f7, 0x7e22ce, 0xc084fc),
            "fuchsia" to Triple(0xd946ef, 0xa21caf, 0xe879f9),
            "pink" to Triple(0xec4899, 0xbe185d, 0xf472b6),
            "rose" to Triple(0xf43f5e, 0xbe123c, 0xfb7185),
            "slate" to Triple(0x64748b, 0x475569, 0x94a3b8),
            "gray" to Triple(0x6b7280, 0x374151, 0x9ca3af),
            "stone" to Triple(0x78716c, 0x44403c, 0xa8a29e)
        )

        /** The tint for an action's `color`, or null for none or one it can't read. */
        fun of(color: String): ActionTint? {
            val deep = color.endsWith(DEEP)
            val base = if (deep) color.dropLast(DEEP.length) else color
            val swatch = Palette[base]
            if (swatch != null) {
                val shade = if (deep) DEEP_SHADE else 1f
                val dark = rgb(swatch.third)
                return ActionTint(
                    light = rgb(swatch.first).darkened(shade),
                    dark = dark.darkened(shade),
                    lightText = rgb(swatch.second),
                    darkText = dark
                )
            }
            val hex = hex(color) ?: return null
            return ActionTint(hex, hex, hex, hex)
        }

        private fun hex(raw: String): Color? {
            val digits = raw.removePrefix("#")
            if (digits.length != HEX_DIGITS) return null
            return digits.toLongOrNull(radix = 16)?.let(::rgb)
        }

        private fun rgb(value: Long): Color = Color(OPAQUE or value)

        private fun Color.darkened(amount: Float): Color =
            Color(red * amount, green * amount, blue * amount, alpha)

        private const val HEX_DIGITS = 6
        private const val OPAQUE = 0xFF000000
    }
}
