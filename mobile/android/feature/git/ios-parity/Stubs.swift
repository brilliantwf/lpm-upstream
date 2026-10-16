import AppKit

// Just enough UIKit for DiffHighlighter.swift and DiffRendering.swift to compile on
// macOS. A color describes itself ("label", "#RRGGBB", or "#light/#dark" for a
// dynamic one) so the harness can print what each run is painted with.

final class UIColor: NSObject {
    let desc: String

    init(desc: String) { self.desc = desc }

    convenience init(red: CGFloat, green: CGFloat, blue: CGFloat, alpha: CGFloat) {
        func byte(_ v: CGFloat) -> Int { Int((v * 255).rounded()) }
        self.init(desc: String(format: "#%02X%02X%02X", byte(red), byte(green), byte(blue)))
    }

    convenience init(dynamicProvider: @escaping (UITraitCollection) -> UIColor) {
        let light = dynamicProvider(UITraitCollection(userInterfaceStyle: .light)).desc
        let dark = dynamicProvider(UITraitCollection(userInterfaceStyle: .dark)).desc
        self.init(desc: light + "/" + dark)
    }

    static let label = UIColor(desc: "label")
    static let secondaryLabel = UIColor(desc: "secondaryLabel")
}

enum UIUserInterfaceStyle { case light, dark }

struct UITraitCollection { let userInterfaceStyle: UIUserInterfaceStyle }

final class UIFont: NSObject {
    struct Weight { static let regular = Weight() }

    static func monospacedSystemFont(ofSize size: CGFloat, weight: Weight) -> UIFont { UIFont() }
}
