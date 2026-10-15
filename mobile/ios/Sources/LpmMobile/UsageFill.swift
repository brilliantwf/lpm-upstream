import SwiftUI

/// A usage bar's fill, turning red from its pace tick on — the use that came
/// faster than even pace. `width` and `aheadFrom` are in points.
struct UsageFill: View {
    let width: CGFloat
    let aheadFrom: CGFloat?
    let cornerRadius: CGFloat
    var tint: Color = usageTint

    var body: some View {
        Rectangle()
            .fill(tint)
            .frame(width: width)
            .overlay(alignment: .leading) {
                if let aheadFrom, aheadFrom < width {
                    Rectangle()
                        .fill(usageAheadTint)
                        .frame(width: width - aheadFrom)
                        .offset(x: aheadFrom)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
    }
}
