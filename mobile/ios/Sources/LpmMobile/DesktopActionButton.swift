import SwiftUI

/// An action as the desktop draws it: its emoji and label on a bordered button
/// washed in the action's color. A group whose `primary` resolves to a child, or
/// that has a command of its own, is a split button — the face runs that child or
/// the group's command, the chevron lists the children — and any other group opens
/// its children.
struct DesktopActionButton: View {
    let action: Action
    let project: String
    var compact = false
    /// Stretches to the width it's offered, as a zone's buttons fill their column.
    var fill = false
    /// Long-press offers Edit Action… when set.
    var onEdit: ((Action) -> Void)? = nil
    let onRun: (Action) -> Void

    private var memory: ActionPrimaryMemory { .shared }

    var body: some View {
        if let onEdit {
            button.contextMenu {
                Button { onEdit(action) } label: {
                    Label("Edit Action…", systemImage: "pencil")
                }
            }
        } else {
            button
        }
    }

    @ViewBuilder
    private var button: some View {
        if action.isRunnable {
            Button { onRun(action) } label: {
                Text(action.displayLabel)
                    .lineLimit(1)
                    .padding(.horizontal, 14)
                    .frame(maxWidth: fill ? .infinity : nil)
                    .desktopActionFace(ActionTint(action.color), compact: compact)
            }
            .buttonStyle(.plain)
        } else if let primary = action.primaryChild(remembered: memory.child(project: project, group: action.name)) {
            split(primary)
        } else if !action.cmd.isEmpty {
            split(action)
        } else {
            Menu {
                ActionMenuItems(actions: action.children, onPick: run)
            } label: {
                HStack(spacing: 6) {
                    Text(action.displayLabel).lineLimit(1)
                    chevron
                }
                .padding(.horizontal, 14)
                .frame(maxWidth: fill ? .infinity : nil)
                .desktopActionFace(ActionTint(action.color), compact: compact)
            }
            .menuStyle(.button)
            .buttonStyle(.plain)
        }
    }

    private func split(_ primary: Action) -> some View {
        HStack(spacing: 0) {
            Button { run(primary) } label: {
                Text(primary.displayLabel)
                    .lineLimit(1)
                    .padding(.leading, 14)
                    .padding(.trailing, 12)
                    .frame(maxWidth: fill ? .infinity : nil, maxHeight: .infinity)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            Rectangle()
                .fill(Self.divider)
                .frame(width: 1)
            Menu {
                ActionMenuItems(actions: action.children, onPick: run)
            } label: {
                chevron
                    .frame(width: 40)
                    .frame(maxHeight: .infinity)
                    .contentShape(Rectangle())
            }
            .menuStyle(.button)
            .buttonStyle(.plain)
            .accessibilityLabel("More \(action.label) Actions")
        }
        .desktopActionFace(ActionTint(primary.color.isEmpty ? action.color : primary.color), compact: compact)
    }

    // The desktop's plain border, but lighter in dark mode, where the system
    // separator disappears into a dark glass button.
    private static let divider = Color(UIColor {
        $0.userInterfaceStyle == .dark ? UIColor(white: 1, alpha: 0.2) : .separator
    })

    private var chevron: some View {
        Image(systemName: "chevron.down")
            .font(.system(size: 11, weight: .bold))
    }

    /// A `last-used` group remembers the direct child it ran, as the desktop does.
    private func run(_ child: Action) {
        if action.primary == Action.lastUsed, action.children.contains(where: { $0.id == child.id }) {
            memory.remember(child.childKey, project: project, group: action.name)
        }
        onRun(child)
    }
}

/// A group's children as menu items, nested groups as submenus.
struct ActionMenuItems: View {
    let actions: [Action]
    let onPick: (Action) -> Void

    var body: some View {
        ForEach(actions) { action in
            if action.isRunnable {
                Button(action.displayLabel) { onPick(action) }
            } else {
                Menu(action.displayLabel) {
                    ActionMenuItems(actions: action.children, onPick: onPick)
                }
            }
        }
    }
}

/// Which child each `primary: last-used` group ran last, per project, kept on
/// this iPhone.
@Observable
final class ActionPrimaryMemory {
    static let shared = ActionPrimaryMemory()

    private static let key = "actionPrimaryLastUsed"
    private var choices = UserDefaults.standard.dictionary(forKey: ActionPrimaryMemory.key) as? [String: String] ?? [:]

    func child(project: String, group: String) -> String? {
        choices["\(project)/\(group)"]
    }

    func remember(_ child: String, project: String, group: String) {
        choices["\(project)/\(group)"] = child
        UserDefaults.standard.set(choices, forKey: Self.key)
    }
}

extension Action {
    static let lastUsed = "last-used"

    /// The label as the desktop writes it, emoji first.
    var displayLabel: String { emoji.isEmpty ? label : "\(emoji) \(label)" }

    /// The child's own name, without its `parent:` path.
    var childKey: String { name.split(separator: ":").last.map(String.init) ?? name }

    /// The child a split button runs: the one `primary` names (or the remembered
    /// one for `last-used`), else the first runnable child. nil without `primary`.
    func primaryChild(remembered: String?) -> Action? {
        guard !primary.isEmpty else { return nil }
        let target = primary == Action.lastUsed ? remembered : primary
        if let target, let match = children.first(where: { $0.childKey == target }) {
            return match
        }
        return children.first(where: \.isRunnable)
    }
}

extension View {
    /// The action button's face, the label in the action's color: on iOS 26 an
    /// interactive Liquid Glass capsule tinted with it, before that the desktop's
    /// bordered rounded rectangle over a light wash of it. Smaller in the footer.
    @ViewBuilder
    func desktopActionFace(_ tint: ActionTint?, compact: Bool = false) -> some View {
        let label = font(.system(size: compact ? 14 : 15, weight: .medium))
            .foregroundStyle(tint?.text ?? .primary)
            .frame(height: 44)
        if #available(iOS 26.0, *) {
            label
                .glassEffect(.regular.tint(tint?.glass).interactive(), in: Capsule())
                .contentShape(Capsule())
        } else {
            let shape = RoundedRectangle(cornerRadius: compact ? 10 : 12, style: .continuous)
            label
                .background(tint?.accent.opacity(0.12) ?? .clear, in: shape)
                .background(Color(.secondarySystemGroupedBackground), in: shape)
                .overlay(shape.strokeBorder(Color(.separator)))
                .contentShape(shape)
        }
    }

    /// Groups nearby glass buttons so iOS 26 renders them as one glass layer.
    @ViewBuilder
    func glassGroup() -> some View {
        if #available(iOS 26.0, *) {
            GlassEffectContainer { self }
        } else {
            self
        }
    }
}
