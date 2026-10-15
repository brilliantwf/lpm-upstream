import SwiftUI

extension View {
    /// Every project action in a sheet. It hangs off the screen, not the nav-bar
    /// button: a sheet presented from inside the bar takes the bar's color scheme,
    /// which the terminal screen forces dark.
    func projectActionsSheet(project: Project, isPresented: Binding<Bool>,
                             onRun: @escaping (Action) -> Void) -> some View {
        modifier(ProjectActionsSheetHost(project: project, isPresented: isPresented, onRun: onRun))
    }
}

/// The picked action runs after the sheet closes, because the run flow may
/// present a sheet of its own.
private struct ProjectActionsSheetHost: ViewModifier {
    let project: Project
    @Binding var isPresented: Bool
    let onRun: (Action) -> Void
    @Environment(AppModel.self) private var model
    @State private var picked: Action?

    func body(content: Content) -> some View {
        content.sheet(isPresented: $isPresented, onDismiss: runPicked) {
            ProjectActionsSheet(project: project) { action in
                picked = action
                isPresented = false
            }
            .environment(model)
        }
    }

    private func runPicked() {
        guard let action = picked else { return }
        picked = nil
        onRun(action)
    }
}
