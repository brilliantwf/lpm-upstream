import SwiftUI

/// Under the projects list: a newer lpm for the live Mac, and the install of it
/// once one runs there, whoever started it — or the answer to a check asked for
/// from the menu.
struct MacUpdateBar: View {
    @Environment(AppModel.self) private var model

    private var store: MacUpdateStore { model.macUpdate }

    /// Off while the Mac is away, except for the restart that takes it away.
    private var visible: Bool {
        guard store.showsBar else { return false }
        switch store.state {
        case .restarting?, .updated?: return true
        default: return model.link.isReady
        }
    }

    var body: some View {
        if visible {
            HStack(spacing: 12) {
                icon
                    .frame(width: 22)
                VStack(alignment: .leading, spacing: 3) {
                    Text(title)
                        .font(.subheadline.weight(.medium))
                        .lineLimit(1)
                    detail
                }
                Spacer(minLength: 4)
                trailing
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14))
            .padding(.horizontal)
            .padding(.bottom, 6)
            .transition(.move(edge: .bottom).combined(with: .opacity))
        }
    }

    private var target: String { store.newer.map { "lpm \($0)" } ?? "lpm" }

    private var title: String {
        switch store.lookup {
        case .checking?: return "Checking for updates…"
        case .upToDate?: return "lpm is up to date"
        case .failed?: return "Couldn't check for updates"
        case nil: break
        }
        if store.error != nil && store.state == nil { return "Couldn't update lpm" }
        switch store.state {
        case .checking?, .downloading?: return "Updating to \(target)"
        case .installing?: return "Installing \(target)…"
        case .restarting?: return "Restarting lpm…"
        case .updated(let release)?: return "Updated to lpm \(release)"
        case nil: return "\(target) is available"
        }
    }

    @ViewBuilder
    private var detail: some View {
        if case .failed(let error)? = store.lookup {
            Text(error)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(2)
        } else if let error = store.error, store.state == nil {
            Text(error)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(2)
        } else {
            switch store.state {
            case .downloading(let percent)? where percent >= 0:
                ProgressView(value: Double(percent), total: 100)
                    .frame(maxWidth: 160)
            default:
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .monospacedDigit()
            }
        }
    }

    private var subtitle: String {
        if store.lookup != nil { return store.version.map { "This Mac runs \($0)" } ?? "" }
        if store.cancelling { return "Cancelling…" }
        switch store.state {
        case .checking?: return "Checking…"
        case .downloading?: return "Downloading…"
        case .installing?: return "lpm restarts when it's done"
        case .restarting?: return "lpm Link reconnects when it's back"
        case .updated?: return "Restarted and reconnected"
        case nil: return store.version.map { "This Mac runs \($0)" } ?? ""
        }
    }

    @ViewBuilder
    private var icon: some View {
        if let lookup = store.lookup {
            switch lookup {
            case .checking: ProgressView().controlSize(.small)
            case .upToDate: Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
            case .failed: Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.orange)
            }
        } else if store.error != nil && store.state == nil {
            Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.orange)
        } else {
            switch store.state {
            case nil:
                Image(systemName: "arrow.down.circle.fill").foregroundStyle(Color.accentColor)
            case .updated?:
                Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
            default:
                ProgressView().controlSize(.small)
            }
        }
    }

    @ViewBuilder
    private var trailing: some View {
        switch store.state {
        case nil where store.lookup == .checking:
            EmptyView()
        case nil where store.lookup != nil:
            close
        case nil:
            HStack(spacing: 4) {
                if store.canInstall {
                    MacUpdateButton {
                        Text(store.error == nil ? "Update" : "Try Again")
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.small)
                }
                close
            }
        case let state? where state.cancellable:
            Button("Cancel") { store.cancel() }
                .buttonStyle(.bordered)
                .controlSize(.small)
                .disabled(store.cancelling)
        default:
            EmptyView()
        }
    }

    private var close: some View {
        Button {
            store.dismiss()
        } label: {
            Image(systemName: "xmark")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(.secondary)
                .frame(width: 28, height: 28)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Close")
    }
}
