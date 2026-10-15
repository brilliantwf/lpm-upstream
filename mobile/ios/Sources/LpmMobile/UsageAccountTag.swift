import SwiftUI

enum UsageAccountMark {
    /// New sessions use this account.
    case current
    /// The rest of the pool, which switching can move to.
    case standby
}

/// The desktop sidebar's "in use" / "pool" tag beside a Claude account's name.
struct UsageAccountTag: View {
    let mark: UsageAccountMark

    var body: some View {
        switch mark {
        case .current:
            Text("in use")
                .font(.caption.weight(.semibold))
                .foregroundStyle(Color(light: "#AE5630", dark: "#DE8A68"))
                .padding(.horizontal, 6)
                .padding(.vertical, 1)
                .background(Color(light: "#D97757", dark: "#DE8A68").opacity(0.16),
                            in: RoundedRectangle(cornerRadius: 5, style: .continuous))
        case .standby:
            Text("pool")
                .font(.caption)
                .foregroundStyle(.secondary)
                .padding(.horizontal, 6)
                .padding(.vertical, 1)
                .overlay(
                    RoundedRectangle(cornerRadius: 5, style: .continuous)
                        .strokeBorder(Color.primary.opacity(0.2), lineWidth: 1)
                )
        }
    }
}
