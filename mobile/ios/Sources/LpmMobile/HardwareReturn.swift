import SwiftUI
import UIKit

/// A hardware keyboard's Return sends and Shift-Return types a newline, as Enter
/// and Shift-Enter do in the desktop's composers; the on-screen Return still
/// starts a new line. A vertical TextField submits and ends editing on a hardware
/// Return whatever a key handler answers, so the handler only notes whether Shift
/// was down and the submit acts on it, handing the keyboard back to the field.
private struct HardwareReturnSends: ViewModifier {
    let focus: FocusState<Bool>.Binding
    let send: () -> Void

    @State private var sends: Bool?

    func body(content: Content) -> some View {
        content
            .onKeyPress(phases: .down) { press in
                if press.key == .return { sends = !press.modifiers.contains(.shift) }
                return .ignored
            }
            .onSubmit {
                guard let sends else { return }
                self.sends = nil
                if sends { send() }
                focus.wrappedValue = true
                guard !sends else { return }
                // Typed in once the field has the keyboard again, so the newline
                // lands at the caret.
                DispatchQueue.main.async {
                    UIApplication.shared.sendAction(#selector(UIKeyInput.insertText(_:)),
                                                    to: nil, from: "\n", for: nil)
                }
            }
    }
}

extension View {
    func hardwareReturnSends(focus: FocusState<Bool>.Binding, send: @escaping () -> Void) -> some View {
        modifier(HardwareReturnSends(focus: focus, send: send))
    }
}
