import ObjectiveC
import SwiftUI
import UIKit

extension View {
    /// Raises the dark keyboard for the text view inside this view, whatever the
    /// system appearance. SwiftUI sets its fields' keyboard appearance back to the
    /// default on every update, and once the terminal's web view has had the
    /// keyboard, iOS turns that default into the light one as the field takes
    /// focus. So the field is pinned to dark before it ever does.
    func darkKeyboard(_ enabled: Bool = true) -> some View {
        background(enabled ? DarkKeyboardScope() : nil)
    }
}

private struct DarkKeyboardScope: UIViewRepresentable {
    func makeUIView(context: Context) -> ScopeView {
        let view = ScopeView()
        view.isUserInteractionEnabled = false
        return view
    }

    func updateUIView(_ view: ScopeView, context: Context) {}

    final class ScopeView: UIView {
        private var observers: [NSObjectProtocol] = []

        override func didMoveToWindow() {
            super.didMoveToWindow()
            observers.forEach(NotificationCenter.default.removeObserver)
            observers = []
            guard window != nil else { return }
            observers = [UIResponder.keyboardWillShowNotification, UITextView.textDidBeginEditingNotification].map { name in
                NotificationCenter.default.addObserver(forName: name, object: nil, queue: nil) { [weak self] _ in
                    self?.pinFields()
                }
            }
            DispatchQueue.main.async { [weak self] in self?.pinFields() }
        }

        deinit { observers.forEach(NotificationCenter.default.removeObserver) }

        // Fields under this view, in the same view controller — not in a sheet
        // that happens to cover it.
        private func pinFields() {
            guard let owner = controllerView else { return }
            let frame = convert(bounds, to: nil)
            func visit(_ view: UIView) {
                if let field = view as? UITextView,
                   frame.contains(field.convert(CGPoint(x: field.bounds.midX, y: field.bounds.midY), to: nil)) {
                    field.pinDarkKeyboard()
                }
                view.subviews.forEach(visit)
            }
            visit(owner)
        }

        private var controllerView: UIView? {
            var view = superview
            while let current = view, !(current.next is UIViewController) { view = current.superview }
            return view
        }
    }
}

private extension UITextView {
    static let darkKeyboardSuffix = "_LpmDarkKeyboard"

    /// Moves the field to a subclass whose keyboard appearance setter always asks
    /// for dark. UITextView has no setter of its own — it forwards the call to its
    /// input traits — so the override sends dark on down that same forwarding path.
    func pinDarkKeyboard() {
        let base: AnyClass = type(of: self)
        guard !NSStringFromClass(base).hasSuffix(Self.darkKeyboardSuffix),
              let subclass = Self.darkKeyboardSubclass(of: base) else { return }
        object_setClass(self, subclass)
        keyboardAppearance = .dark
    }

    static func darkKeyboardSubclass(of base: AnyClass) -> AnyClass? {
        let name = NSStringFromClass(base) + darkKeyboardSuffix
        if let existing = NSClassFromString(name) { return existing }
        let setter = #selector(setter: UITextView.keyboardAppearance)
        guard let inherited = class_getMethodImplementation(base, setter),
              let subclass = objc_allocateClassPair(base, name, 0) else { return nil }
        typealias Setter = @convention(c) (AnyObject, Selector, Int) -> Void
        let set = unsafeBitCast(inherited, to: Setter.self)
        let dark: @convention(block) (AnyObject, Int) -> Void = { field, _ in
            set(field, setter, UIKeyboardAppearance.dark.rawValue)
        }
        class_addMethod(subclass, setter, imp_implementationWithBlock(dark), "v@:q")
        objc_registerClassPair(subclass)
        return subclass
    }
}
