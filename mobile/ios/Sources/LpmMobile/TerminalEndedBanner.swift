import SwiftUI

/// Over a terminal whose process is gone, so its last output stays readable but
/// nobody keeps typing into it. Styled like the reconnecting banner.
struct TerminalEndedBanner: View {
    let text: String
    var icon = "stop.circle"

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
            Text(text).lineLimit(1)
        }
        .font(.footnote)
        .foregroundStyle(.white)
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(.black.opacity(0.6), in: Capsule())
        .overlay(Capsule().strokeBorder(.white.opacity(0.15)))
        .environment(\.colorScheme, .dark)
        .padding(.horizontal, 16)
    }
}

/// How a terminal ended: its process exited with `code`, or the Mac no longer
/// has it at all (lpm restarted, or the exit itself never reached the phone).
struct TerminalEnding: Equatable {
    let code: Int
    var gone: Bool
    /// The tab's name and place in its project's list when it ended, to find
    /// the tab lpm reopens in its place.
    var label: String?
    var index: Int?
    var at = Date()
    /// The link dropped right after it ended: lpm quit under it, rather than
    /// its process exiting on its own.
    var restarted = false

    /// lpm no longer has the tab, so one it reopened can stand in for it.
    var replaceable: Bool { gone || restarted }

    var message: String {
        if restarted { return "Closed when lpm restarted" }
        if gone { return "This terminal is no longer running" }
        return code == 0 ? "Process exited" : "Process exited with code \(code)"
    }
}
