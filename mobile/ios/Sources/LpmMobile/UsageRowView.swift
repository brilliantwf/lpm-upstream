import SwiftUI

/// One usage row, as the desktop sidebar draws it: the tool's dot, the account's
/// name and tag, when its window comes back and how much of it is gone, over a
/// two-lane bar: that window above with its pace tick, the other one below.
/// Tapping opens what the desktop shows on hover — both
/// windows, the plan, who uses the account and the day's spend — and, on a
/// project's screen, switching that project to the account.
struct UsageRowView: View {
    let row: UsageRow
    let mark: UsageAccountMark?
    let inUse: ClaudeInUse?
    let expanded: Bool
    let now: Double
    let macNow: Double
    let toggle: () -> Void
    /// Pins the project's new Claude sessions to an account; nil off a project.
    var onSwitch: ((String) -> Void)?

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button(action: toggle) {
                VStack(alignment: .leading, spacing: 9) {
                    header
                    if let lane = row.lane {
                        UsageLanesView(upper: UsageLane(fraction: row.fraction, fill: row.fill), pace: row.pace,
                                       lower: lane, dim: row.stale)
                    } else {
                        UsagePaceBar(fraction: row.fraction, fill: row.fill, pace: row.pace, dim: row.stale)
                    }
                }
                .padding(.vertical, 6)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(accessibilityLabel)
            .accessibilityValue(accessibilityValue)
            .accessibilityHint(expanded ? "Hides the details" : "Shows both windows")
            .accessibilityAddTraits(.isButton)

            if expanded {
                UsageRowDetail(row: row, inUse: inUse, switchTarget: switchTarget,
                               now: now, macNow: macNow, onSwitch: onSwitch)
                    .padding(.top, 8)
                    .padding(.bottom, 6)
                    .transition(.opacity)
            }
        }
        .contextMenu {
            if let switchTarget, let onSwitch, let project = inUse?.project {
                Button { onSwitch(switchTarget) } label: {
                    Label {
                        Text("Switch to \(row.label)")
                        Text("New sessions in \(project.label)")
                    } icon: {
                        Image(systemName: inUse?.current == switchTarget ? "checkmark" : "person.crop.circle")
                    }
                }
                .disabled(pinnedHere)
            }
        }
    }

    private var header: some View {
        HStack(spacing: 8) {
            Circle()
                .fill(row.color)
                .frame(width: 8, height: 8)
                .opacity(row.stale ? 0.5 : 1)
            Text(row.label)
                .font(.subheadline.weight(.medium))
                .lineLimit(1)
            if let mark {
                UsageAccountTag(mark: mark)
            }
            Spacer(minLength: 4)
            if !row.windowShort.isEmpty {
                Text(row.windowShort)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            Text(row.detail)
                .font(.footnote)
                .monospacedDigit()
                .foregroundStyle(.secondary)
                .lineLimit(1)
            if !row.percentText.isEmpty {
                Text(row.percentText)
                    .font(.subheadline.weight(.semibold))
                    .monospacedDigit()
                    .foregroundStyle(row.stale ? Color.secondary : Color.primary)
                    .frame(minWidth: 40, alignment: .trailing)
            }
            Image(systemName: expanded ? "chevron.down" : "chevron.right")
                .font(.caption2.weight(.semibold))
                .foregroundStyle(.tertiary)
                .frame(width: 12)
        }
    }

    /// The account a project's screen can switch to: a Claude account row, on a
    /// project whose sessions run on this Mac.
    private var switchTarget: String? {
        guard onSwitch != nil, inUse?.project != nil else { return nil }
        return row.account
    }

    private var pinnedHere: Bool {
        inUse?.pinned == true && inUse?.current == switchTarget
    }

    private var accessibilityLabel: String {
        let tag: String? = switch mark {
        case .current: "in use"
        case .standby: "pool"
        case nil: nil
        }
        return [row.label, tag].compactMap { $0 }.joined(separator: ", ")
    }

    private var accessibilityValue: String {
        guard !row.percentText.isEmpty else { return row.detail }
        let main = "\(row.percentText) of the \(row.windowLabel) window used, resets in \(row.detail)"
        return row.laneText.isEmpty ? main : "\(main); \(row.laneText)"
    }
}
