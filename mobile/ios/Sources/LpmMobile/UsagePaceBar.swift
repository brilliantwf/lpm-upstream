import SwiftUI

/// A usage row's thin bar with its pace tick — where even use would put the bar
/// by now — standing 3pt proud of the bar, as the desktop sidebar draws it.
struct UsagePaceBar: View {
    let fraction: Double
    let fill: Color
    let pace: Double?
    var dim = false
    var height: CGFloat = 4

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GeometryReader { geo in
            let width = geo.size.width
            ZStack(alignment: .leading) {
                Capsule().fill(Color(.tertiarySystemFill))
                Capsule()
                    .fill(fill)
                    .frame(width: fillWidth(width))
                    .opacity(dim ? 0.4 : 1)
            }
            .frame(height: height)
            .overlay(alignment: .leading) {
                if let pace {
                    Capsule()
                        .fill(Color.primary)
                        .frame(width: 2, height: height + 6)
                        .offset(x: max(0, min(width - 2, pace * width - 1)))
                        .opacity(dim ? 0.4 : 1)
                }
            }
            .animation(reduceMotion ? nil : .easeOut(duration: 0.5), value: fraction)
        }
        .frame(height: height)
    }

    /// Any use at all shows as a sliver, so 1% never reads as nothing.
    private func fillWidth(_ width: CGFloat) -> CGFloat {
        guard fraction > 0 else { return 0 }
        return width * max(0.02, min(1, fraction))
    }
}
