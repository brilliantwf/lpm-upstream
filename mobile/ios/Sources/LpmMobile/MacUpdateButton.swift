import SwiftUI

/// Starts the update on the live Mac, with a warning first when agents there
/// are mid-task: the restart the update ends in would cut them off.
struct MacUpdateButton<Label: View>: View {
    @Environment(AppModel.self) private var model
    @State private var busy: [BusyAgentPlace] = []
    @State private var confirming = false
    @ViewBuilder let label: () -> Label

    private var total: Int { busy.reduce(0) { $0 + $1.count } }

    var body: some View {
        Button {
            busy = model.macUpdate.busyPlaces
            if busy.isEmpty {
                model.macUpdate.install()
            } else {
                confirming = true
            }
        } label: {
            label()
        }
        .disabled(!model.macUpdate.canInstall || !model.link.isReady)
        .confirmationDialog(
            total == 1 ? "An agent is still running" : "Agents are still running",
            isPresented: $confirming,
            titleVisibility: .visible
        ) {
            Button("Update Anyway", role: .destructive) { model.macUpdate.install() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text(message)
        }
    }

    private var message: String {
        let mac = model.link.macLabel
        let agents = total == 1 ? "an agent" : "\(total) agents"
        let places = busy.map(\.label).formatted(.list(type: .and))
        return "Updating restarts lpm on \(mac), which stops \(agents) mid-task in \(places)."
    }
}
