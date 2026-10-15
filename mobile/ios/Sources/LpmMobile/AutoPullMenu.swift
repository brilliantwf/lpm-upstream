import SwiftUI

/// The Git menu's auto pull items, as on the desktop: "Auto Pull ▸" with the
/// project's own choice (follow, On, Off), greyed out while the Mac has auto
/// pull off entirely, then Undo Auto Pull while lpm's last one can be undone.
struct AutoPullMenuItems: View {
    @Environment(AppModel.self) private var model
    let project: Project

    private static let choices: [Bool?] = [nil, true, false]

    private var mode: AutoPullMode { model.autoPullMode }
    private var parent: Project? { model.parentProject(of: project) }
    private var autoPulled: AutoPulled? { model.git.snapshots[project.name]?.autoPulled }
    private var undoing: Bool { model.git.undoingAutoPull.contains(project.name) }

    var body: some View {
        if mode == .off {
            Button {} label: {
                Label {
                    Text("Auto Pull")
                    Text("Off in the Mac's Settings")
                } icon: {
                    Image(systemName: "arrow.down.circle")
                }
            }
            .disabled(true)
        } else {
            Menu {
                ForEach(Self.choices, id: \.self) { value in
                    Button { pick(value) } label: { choiceLabel(value) }
                }
            } label: {
                Label {
                    Text("Auto Pull")
                    Text(title(project.autoPull))
                } icon: {
                    Image(systemName: "arrow.down.circle")
                }
            }
        }
        if let autoPulled {
            Button { model.git.undoAutoPull(project.name) } label: {
                Label("Undo Auto Pull", systemImage: "arrow.uturn.backward")
                Text(autoPulled.summary)
            }
            .disabled(undoing)
        }
    }

    @ViewBuilder
    private func choiceLabel(_ value: Bool?) -> some View {
        if project.autoPull == value {
            Label(title(value), systemImage: "checkmark")
        } else {
            Text(title(value))
        }
    }

    /// Following names what it resolves to right now, so the menu reads the
    /// same as the desktop's: "Default (On)", or "Same as <parent> (Off)".
    private func title(_ value: Bool?) -> String {
        if let value { return value ? "On" : "Off" }
        let resolved = autoPullEnabled(own: nil, parent: parent, mode: mode) ? "On" : "Off"
        guard project.isDuplicate else { return "Default (\(resolved))" }
        return "Same as \(parent?.label ?? project.parentName) (\(resolved))"
    }

    private func pick(_ value: Bool?) {
        guard value != project.autoPull else { return }
        model.setAutoPull(project.name, value)
    }
}
