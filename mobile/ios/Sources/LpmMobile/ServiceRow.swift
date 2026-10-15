import SwiftUI

/// One configured service: its name and port, with a switch that starts or
/// stops it (a spinner while a toggle is in flight). One whose command ended
/// says how, in red when it failed, and offers Restart in place of the switch.
/// Tapping the row opens its logs.
struct ServiceRow: View {
    let service: Service
    let running: Bool
    /// How it ended, while its pane is still open; nil when it runs or never started.
    let ended: Service?
    let pending: Bool
    /// The Mac restarts and stops services outright (`serviceRestart`).
    let canStop: Bool
    let onToggle: () -> Void
    let onRestart: () -> Void
    let onStop: () -> Void
    let onOpen: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            // The logs tap target stops short of the switch: a row-wide tap
            // gesture would take the switch's taps for itself.
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 8) {
                    Text(service.name).lineLimit(1)
                    if service.port > 0 {
                        Text(":\(String(service.port))")
                            .monospacedDigit()
                            .foregroundStyle(.secondary)
                    }
                }
                if let ended {
                    HStack(spacing: 5) {
                        Circle()
                            .fill(ended.crashed ? Color.red : Color.secondary)
                            .frame(width: 6, height: 6)
                        Text(ended.exitText)
                            .font(.caption)
                            .foregroundStyle(ended.crashed ? Color.red : Color.secondary)
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            .contentShape(Rectangle())
            .onTapGesture(perform: onOpen)
            if pending {
                ProgressView().controlSize(.small)
            } else if ended != nil {
                Button(action: onRestart) {
                    Label("Restart", systemImage: "arrow.clockwise")
                        .font(.subheadline.weight(.medium))
                }
                .buttonStyle(.borderless)
                .accessibilityLabel("Restart \(service.name)")
            } else {
                Toggle(service.name, isOn: Binding(get: { running }, set: { _ in onToggle() }))
                    .labelsHidden()
            }
        }
        .frame(minHeight: 44)
        .contextMenu {
            if ended != nil || (running && canStop) {
                Button(action: onRestart) {
                    Label("Restart", systemImage: "arrow.clockwise")
                }
                if canStop {
                    Button(role: .destructive, action: onStop) {
                        Label("Stop", systemImage: "stop.fill")
                    }
                }
            }
        }
    }
}
