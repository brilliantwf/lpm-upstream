import SwiftUI

/// The Mac can't read the project's YAML: why, and the raw file — the one editor
/// that can fix it — as the desktop offers Edit Config and Retry.
struct ProjectConfigErrorView: View {
    @Environment(AppModel.self) private var model
    let project: Project
    let message: String

    @State private var editing = false

    var body: some View {
        ContentUnavailableView {
            Label {
                Text("Invalid configuration")
            } icon: {
                Image(systemName: "exclamationmark.circle").foregroundStyle(.red)
            }
        } description: {
            Text(message)
                .font(.footnote.monospaced())
                .foregroundStyle(.red)
        } actions: {
            Button("Edit Configuration File") { editing = true }
                .buttonStyle(.borderedProminent)
            Button("Retry") { Task { await model.refreshProjects() } }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.systemGroupedBackground))
        .sheet(isPresented: $editing) {
            YamlConfigEditorSheet(project: project).environment(model)
        }
    }
}
