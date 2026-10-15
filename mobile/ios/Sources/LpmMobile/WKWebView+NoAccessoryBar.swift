import ObjectiveC
import UIKit
import WebKit

extension WKWebView {
    private static let noAccessorySuffix = "_LpmNoAccessoryBar"

    /// Drops the ⌃ ⌄ ✓ bar WebKit puts above the keyboard for a focused page
    /// field. WebKit has no setting for it, so the content view moves to a
    /// subclass whose `inputAccessoryView` is nil.
    func hideInputAccessoryBar() {
        guard let content = scrollView.subviews.first(where: {
            NSStringFromClass(type(of: $0)).hasPrefix("WKContent")
        }) else { return }
        let base: AnyClass = type(of: content)
        guard !NSStringFromClass(base).hasSuffix(Self.noAccessorySuffix),
              let subclass = Self.noAccessorySubclass(of: base) else { return }
        object_setClass(content, subclass)
    }

    private static func noAccessorySubclass(of base: AnyClass) -> AnyClass? {
        let name = NSStringFromClass(base) + noAccessorySuffix
        if let existing = NSClassFromString(name) { return existing }
        let getter = #selector(getter: UIResponder.inputAccessoryView)
        guard let subclass = objc_allocateClassPair(base, name, 0),
              let method = class_getInstanceMethod(base, getter) else { return nil }
        let none: @convention(block) (AnyObject) -> UIView? = { _ in nil }
        class_addMethod(subclass, getter, imp_implementationWithBlock(none), method_getTypeEncoding(method))
        objc_registerClassPair(subclass)
        return subclass
    }
}
