import SwiftUI
import UIKit

enum Keyboard {
    static func dismiss() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }
}

extension View {
    /// Closes the keyboard on a downward swipe over this view, as Messages does.
    /// Disabled, it leaves the view's own gestures (a field scrolling its text)
    /// to themselves.
    func swipeDownDismissesKeyboard(_ enabled: Bool = true) -> some View {
        simultaneousGesture(
            DragGesture(minimumDistance: 20).onEnded { value in
                let down = value.translation.height
                if down > 40, down > abs(value.translation.width) * 2 { Keyboard.dismiss() }
            },
            including: enabled ? .all : .subviews
        )
    }
}
