import SwiftUI

/// Over a terminal whose process is gone, so its last output stays readable but
/// nobody keeps typing into it. Styled like the reconnecting banner.
struct TerminalEndedBanner: View {
    let ending: TerminalEnding

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "stop.circle")
            Text(ending.message).lineLimit(1)
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
    let gone: Bool

    var message: String {
        if gone { return "This terminal is no longer running" }
        return code == 0 ? "Process exited" : "Process exited with code \(code)"
    }
}
