import SwiftUI
import UIKit
import UIKit.UIGestureRecognizerSubclass

/// Calls `onTouch` whenever a finger lands anywhere in the window this view sits
/// in, without taking part in the touch, so the terminal, the composer and the nav
/// bar all still get it. The phone's counterpart of the desktop's attention.ts: a
/// deliberate touch is the proof that someone is looking at the screen.
struct WindowTouchObserver: UIViewRepresentable {
    let onTouch: () -> Void

    func makeUIView(context: Context) -> ObserverView {
        let view = ObserverView()
        view.isUserInteractionEnabled = false
        return view
    }

    func updateUIView(_ view: ObserverView, context: Context) {
        view.recognizer.onTouch = onTouch
    }

    final class ObserverView: UIView {
        let recognizer = PassiveTouchRecognizer()

        override func didMoveToWindow() {
            super.didMoveToWindow()
            recognizer.view?.removeGestureRecognizer(recognizer)
            window?.addGestureRecognizer(recognizer)
        }
    }
}

final class PassiveTouchRecognizer: UIGestureRecognizer, UIGestureRecognizerDelegate {
    var onTouch: () -> Void = {}

    init() {
        super.init(target: nil, action: nil)
        cancelsTouchesInView = false
        delaysTouchesBegan = false
        delaysTouchesEnded = false
        delegate = self
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        onTouch()
        state = .failed
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer,
                           shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
        true
    }
}
