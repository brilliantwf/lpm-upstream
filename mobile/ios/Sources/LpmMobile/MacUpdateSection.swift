import SwiftUI

/// The lpm a Mac runs, in its connection details, and the update to a newer one
/// — still offered here after the bar over the projects list was closed.
struct MacUpdateSection: View {
    @Environment(AppModel.self) private var model

    private var store: MacUpdateStore { model.macUpdate }

    var body: some View {
        if let version = store.version {
            Section {
                LabeledContent("Version", value: version)
                status
                if let error = store.error, store.state == nil {
                    Text(error)
                        .font(.footnote)
                        .foregroundStyle(.red)
                }
            } header: {
                Text("lpm")
            } footer: {
                if store.canUpdate {
                    Text("Updating restarts lpm there, which stops agents mid-task. Running services keep going.")
                }
            }
        }
    }

    @ViewBuilder
    private var status: some View {
        switch store.state {
        case nil:
            if let newer = store.newer, store.canInstall {
                MacUpdateButton {
                    Label("Update to \(newer)", systemImage: "arrow.down.circle")
                }
            } else if store.canUpdate {
                LabeledContent("Status", value: "Up to date")
            }
        case .updated(let release)?:
            LabeledContent("Status") {
                Label("Updated to \(release)", systemImage: "checkmark.circle.fill")
                    .foregroundStyle(.green)
            }
        case let state?:
            HStack(spacing: 12) {
                ProgressView().controlSize(.small)
                Text(progressText(state))
                    .monospacedDigit()
                Spacer()
                if state.cancellable {
                    Button("Cancel") { store.cancel() }
                        .disabled(store.cancelling)
                }
            }
        }
    }

    private func progressText(_ state: MacUpdateStore.State) -> String {
        if store.cancelling { return "Cancelling…" }
        switch state {
        case .checking: return "Checking…"
        case .downloading(let percent): return percent >= 0 ? "Downloading · \(percent)%" : "Downloading…"
        case .installing: return "Installing…"
        case .restarting: return "Restarting lpm…"
        case .updated: return ""
        }
    }
}
