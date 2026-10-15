import SwiftUI

/// What an expanded usage row shows: the card the desktop opens on hover, and on
/// a project's screen a button that switches the project to the row's account —
/// the desktop's "Switch to …" on a right-click.
struct UsageRowDetail: View {
    let row: UsageRow
    let inUse: ClaudeInUse?
    let switchTarget: String?
    let now: Double
    let macNow: Double
    let onSwitch: ((String) -> Void)?

    private var footer: [String] {
        [row.account.flatMap { inUse?.note(for: $0) }, row.spentLine].compactMap { $0 }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let switchTarget, let onSwitch, let project = inUse?.project {
                switchButton(switchTarget, project: project, action: onSwitch)
            }
            if let data = row.data {
                UsageProviderCardView(data: data, now: now, macNow: macNow,
                                      title: row.label, subtitle: row.subtitle,
                                      footer: footer, fill: Color(.tertiarySystemGroupedBackground))
            } else {
                waiting
            }
        }
    }

    private func switchButton(_ account: String, project: Project,
                              action: @escaping (String) -> Void) -> some View {
        let current = inUse?.current == account
        let pinned = current && inUse?.pinned == true
        return Button { action(account) } label: {
            HStack(spacing: 10) {
                Image(systemName: current ? "checkmark.circle.fill" : "arrow.left.arrow.right.circle")
                    .font(.title3)
                VStack(alignment: .leading, spacing: 1) {
                    Text(pinned ? "Using \(row.label)"
                         : current ? "Always use \(row.label)" : "Switch to \(row.label)")
                        .font(.subheadline.weight(.semibold))
                    Text(pinned ? "New sessions in \(project.label) always use it"
                         : current ? "New sessions in \(project.label) use it now"
                         : "New sessions in \(project.label)")
                        .font(.caption)
                        .opacity(0.8)
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.vertical, 4)
        }
        .buttonStyle(.bordered)
        .buttonBorderShape(.roundedRectangle(radius: 14))
        .tint(current ? .secondary : .accentColor)
        .disabled(pinned)
    }

    /// A row with nothing but the day's spend, or an account that hasn't run
    /// yet, has no windows to draw — the desktop's plain tooltip.
    private var waiting: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(row.label)
                .font(.subheadline.weight(.semibold))
            if let subtitle = row.subtitle {
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            ForEach(footer, id: \.self) { line in
                Text(line)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .monospacedDigit()
            }
            Text("No usage window reported yet.")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Color(.tertiarySystemGroupedBackground),
                    in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}
