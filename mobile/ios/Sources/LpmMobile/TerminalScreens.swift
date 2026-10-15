import Foundation

/// What a terminal screen hands the model so the stream, the composer and the
/// "@ terminal output" mention reach its web view.
struct TerminalHooks {
    let seed: (_ cols: Int, _ rows: Int, _ data: String, _ reset: Bool) -> Void
    let output: (String) -> Void
    let submit: (String) -> Void
    let capture: (_ lines: Int, _ done: @escaping (String) -> Void) -> Void
}

/// The screens showing each terminal, oldest first. Two can show the same
/// terminal for a moment — tapping its notification pushes a fresh copy while the
/// one opened from the project list is still closing — so the hooks follow the
/// newest copy, and only the last copy to close lets the stream go.
final class TerminalScreens {
    enum Closed {
        /// An older copy closed; the newest one keeps the stream.
        case covered
        /// The newest copy closed and an older one is showing again.
        case revealed
        /// No screen shows the terminal any more.
        case last
    }

    private var stacks: [String: [(key: ObjectIdentifier, hooks: TerminalHooks)]] = [:]

    func current(_ id: String) -> TerminalHooks? { stacks[id]?.last?.hooks }

    func open(_ id: String, key: AnyObject, hooks: TerminalHooks) {
        stacks[id, default: []].append((ObjectIdentifier(key), hooks))
    }

    func close(_ id: String, key: AnyObject) -> Closed {
        guard var stack = stacks[id] else { return .last }
        guard let index = stack.firstIndex(where: { $0.key == ObjectIdentifier(key) }) else { return .covered }
        let wasNewest = index == stack.count - 1
        stack.remove(at: index)
        if stack.isEmpty {
            stacks[id] = nil
            return .last
        }
        stacks[id] = stack
        return wasNewest ? .revealed : .covered
    }
}
