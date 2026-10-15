import SwiftUI

/// Both limit windows on one track, as the desktop sidebar draws them: the
/// row's own window in the bolder upper lane, the other in a hairline lane
/// under it. Only the upper lane draws a pace tick, pointing up out of the track.
struct UsageLanesView: View {
    let upper: UsageLane
    let pace: Double?
    let lower: UsageLane
    var dim = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private let upperHeight: CGFloat = 3.5
    private let gap: CGFloat = 1.5
    private let lowerHeight: CGFloat = 1.5
    private let reach: CGFloat = 3

    var body: some View {
        GeometryReader { geo in
            let width = geo.size.width
            VStack(spacing: gap) {
                lane(upper, height: upperHeight, width: width, aheadFrom: pace)
                lane(lower, height: lowerHeight, width: width)
            }
            .clipShape(Capsule())
            .overlay(alignment: .topLeading) {
                if let pace {
                    Capsule()
                        .fill(Color.primary)
                        .frame(width: 2, height: upperHeight + reach)
                        .offset(x: max(0, min(width - 2, pace * width - 1)), y: -reach)
                        .opacity(dim ? 0.4 : 1)
                }
            }
            .animation(reduceMotion ? nil : .easeOut(duration: 0.5), value: upper.fraction)
            .animation(reduceMotion ? nil : .easeOut(duration: 0.5), value: lower.fraction)
        }
        .frame(height: upperHeight + gap + lowerHeight)
    }

    private func lane(_ lane: UsageLane, height: CGFloat, width: CGFloat, aheadFrom pace: Double? = nil) -> some View {
        ZStack(alignment: .leading) {
            Rectangle().fill(Color(.tertiarySystemFill))
            UsageFill(width: fillWidth(lane.fraction, width: width), aheadFrom: pace.map { CGFloat($0) * width },
                      cornerRadius: height / 2, tint: lane.fill)
                .opacity(dim ? 0.4 : 1)
        }
        .frame(height: height)
    }

    /// Any use at all shows as a sliver, so 1% never reads as nothing.
    private func fillWidth(_ fraction: Double, width: CGFloat) -> CGFloat {
        guard fraction > 0 else { return 0 }
        return width * max(0.02, min(1, fraction))
    }
}
