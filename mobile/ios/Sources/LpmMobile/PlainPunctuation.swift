import SwiftUI
import UIKit

/// What is typed reaches a shell, so it must arrive as typed: iOS's smart dashes
/// turn `--flag` into `—flag` and smart quotes curl the quotes a command relies
/// on. SwiftUI's `TextField` can't turn them off, so once the field has the
/// keyboard its backing text view is switched to plain substitutions.
private struct PlainPunctuation: ViewModifier {
    let focused: Bool

    func body(content: Content) -> some View {
        content
            .onAppear { if focused { apply() } }
            .onChange(of: focused) { _, now in if now { apply() } }
    }

    private func apply() {
        DispatchQueue.main.async {
            switch FirstResponder.current() {
            case let view as UITextView where view.smartDashesType != .no || view.smartQuotesType != .no:
                view.smartDashesType = .no
                view.smartQuotesType = .no
                view.reloadInputViews()
            case let field as UITextField where field.smartDashesType != .no || field.smartQuotesType != .no:
                field.smartDashesType = .no
                field.smartQuotesType = .no
                field.reloadInputViews()
            default:
                break
            }
        }
    }
}

private enum FirstResponder {
    static weak var found: UIResponder?

    static func current() -> UIResponder? {
        found = nil
        UIApplication.shared.sendAction(#selector(UIResponder.lpmReportFirstResponder), to: nil, from: nil, for: nil)
        return found
    }
}

private extension UIResponder {
    @objc func lpmReportFirstResponder() {
        FirstResponder.found = self
    }
}

extension View {
    func plainPunctuation(focused: Bool) -> some View {
        modifier(PlainPunctuation(focused: focused))
    }
}
