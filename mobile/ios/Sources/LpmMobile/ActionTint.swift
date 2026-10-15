import SwiftUI
import UIKit

/// An action's `color:` as the desktop paints it: a named accent, optionally
/// `-deep` (mixed toward black), that follows light and dark mode, or a raw
/// `#rrggbb`. Buttons wash their fill with `accent` and draw the label in `text`;
/// Liquid Glass takes `glass`, a fainter wash on light backgrounds than on dark.
struct ActionTint {
    let accent: Color
    let glass: Color
    let text: Color

    init?(_ color: String) {
        let deep = color.hasSuffix("-deep")
        let base = deep ? String(color.dropLast("-deep".count)) : color
        let light: RGB, dark: RGB, lightText: RGB
        if let swatch = Self.palette[base] {
            let shade = deep ? 0.74 : 1
            (light, dark, lightText) = (swatch.light.darkened(shade), swatch.dark.darkened(shade), swatch.lightText)
        } else if let rgb = RGB(hex: color) {
            (light, dark, lightText) = (rgb, rgb, rgb)
        } else {
            return nil
        }
        accent = Self.adaptive(light: light, dark: dark)
        glass = Self.adaptive(light: light, dark: dark, lightAlpha: 0.10, darkAlpha: 0.22)
        text = Self.adaptive(light: lightText, dark: Self.palette[base]?.dark ?? dark)
    }

    /// The named colors in the desktop picker's order: a hue wheel, then neutrals.
    static let names = [
        "red", "orange", "claude", "amber", "yellow", "lime", "green", "emerald", "teal",
        "cyan", "sky", "blue", "indigo", "violet", "purple", "fuchsia", "pink", "rose",
        "slate", "gray", "stone",
    ]

    private static func adaptive(light: RGB, dark: RGB, lightAlpha: Double = 1, darkAlpha: Double = 1) -> Color {
        Color(UIColor {
            $0.userInterfaceStyle == .dark ? dark.uiColor(alpha: darkAlpha) : light.uiColor(alpha: lightAlpha)
        })
    }

    /// The desktop theme's light `--accent-<name>`, light `--accent-<name>-text`
    /// and dark `--accent-<name>`; in dark mode the text is the accent itself.
    private static let hexes: [String: (Int, Int, Int)] = [
        "red": (0xef4444, 0xb91c1c, 0xf87171),
        "orange": (0xf97316, 0xc2410c, 0xfb923c),
        "claude": (0xd97757, 0xae5630, 0xde8a68),
        "amber": (0xf59e0b, 0xb45309, 0xfbbf24),
        "yellow": (0xeab308, 0x854d0e, 0xfacc15),
        "lime": (0x84cc16, 0x3f6212, 0xa3e635),
        "green": (0x22c55e, 0x166534, 0x4ade80),
        "emerald": (0x10b981, 0x047857, 0x34d399),
        "teal": (0x14b8a6, 0x0f766e, 0x2dd4bf),
        "cyan": (0x06b6d4, 0x155e75, 0x22d3ee),
        "sky": (0x0ea5e9, 0x0369a1, 0x38bdf8),
        "blue": (0x3b82f6, 0x1d4ed8, 0x60a5fa),
        "indigo": (0x6366f1, 0x4338ca, 0x818cf8),
        "violet": (0x8b5cf6, 0x6d28d9, 0xa78bfa),
        "purple": (0xa855f7, 0x7e22ce, 0xc084fc),
        "fuchsia": (0xd946ef, 0xa21caf, 0xe879f9),
        "pink": (0xec4899, 0xbe185d, 0xf472b6),
        "rose": (0xf43f5e, 0xbe123c, 0xfb7185),
        "slate": (0x64748b, 0x475569, 0x94a3b8),
        "gray": (0x6b7280, 0x374151, 0x9ca3af),
        "stone": (0x78716c, 0x44403c, 0xa8a29e),
    ]

    private static let palette = hexes.mapValues { (light: RGB($0.0), lightText: RGB($0.1), dark: RGB($0.2)) }
}

private struct RGB {
    let r, g, b: Double

    init(_ hex: Int) {
        r = Double((hex >> 16) & 0xFF) / 255
        g = Double((hex >> 8) & 0xFF) / 255
        b = Double(hex & 0xFF) / 255
    }

    init?(hex: String) {
        let raw = hex.hasPrefix("#") ? String(hex.dropFirst()) : hex
        guard raw.count == 6, let value = Int(raw, radix: 16) else { return nil }
        self.init(value)
    }

    private init(r: Double, g: Double, b: Double) {
        self.r = r; self.g = g; self.b = b
    }

    func darkened(_ amount: Double) -> RGB { RGB(r: r * amount, g: g * amount, b: b * amount) }

    func uiColor(alpha: Double) -> UIColor { UIColor(red: r, green: g, blue: b, alpha: alpha) }
}
