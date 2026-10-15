import UIKit

/// How far the keyboard covers the window above its bottom safe area.
final class KeyboardObserver: ObservableObject {
    @Published private(set) var height: CGFloat = 0
    /// `height` once the keyboard has finished opening; on closing it drops at
    /// once. Lets a view resize its content a single time, at the end of the
    /// opening and at the start of the closing, instead of every animation frame.
    @Published private(set) var settledHeight: CGFloat = 0
    /// False only once the keyboard has finished closing.
    @Published private(set) var isShown = false

    private let center: NotificationCenter
    private var observers: [NSObjectProtocol] = []

    init(center: NotificationCenter = .default) {
        self.center = center
        observers = [
            center.addObserver(
                forName: UIResponder.keyboardWillChangeFrameNotification,
                object: nil,
                queue: .main
            ) { [weak self] notification in
                self?.update(from: notification)
            },
            center.addObserver(
                forName: UIResponder.keyboardWillHideNotification,
                object: nil,
                queue: .main
            ) { [weak self] notification in
                self?.update(from: notification, hidden: true)
            },
            center.addObserver(
                forName: UIResponder.keyboardDidChangeFrameNotification,
                object: nil,
                queue: .main
            ) { [weak self] _ in
                guard let self, self.settledHeight != self.height else { return }
                self.settledHeight = self.height
            },
            center.addObserver(
                forName: UIResponder.keyboardDidHideNotification,
                object: nil,
                queue: .main
            ) { [weak self] _ in
                self?.isShown = false
            }
        ]
    }

    deinit {
        observers.forEach(center.removeObserver)
    }

    private func update(from notification: Notification, hidden: Bool = false) {
        if !hidden, let frame = notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect {
            height = keyboardOverlap(for: frame)
        } else {
            height = 0
        }
        if height > 0 { isShown = true }
        if height < settledHeight { settledHeight = height }
    }

    private func keyboardOverlap(for screenFrame: CGRect) -> CGFloat {
        guard let window = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .flatMap(\.windows)
            .first(where: { $0.isKeyWindow }) else {
            return max(0, UIScreen.main.bounds.maxY - screenFrame.minY)
        }

        let frame = window.convert(screenFrame, from: nil)
        return max(0, window.bounds.maxY - frame.minY - window.safeAreaInsets.bottom)
    }
}
