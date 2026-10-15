import SwiftUI

/// Actions the Mac left out because of a config problem, each with what's wrong,
/// so a missing button doesn't go unexplained.
struct ActionErrorsNotice: View {
    let errors: [String]

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Label(errors.count == 1 ? "An action couldn't be loaded" : "Some actions couldn't be loaded",
                  systemImage: "exclamationmark.triangle.fill")
                .font(.footnote.weight(.semibold))
            ForEach(errors, id: \.self) { error in
                Text(error)
                    .font(.caption)
                    .textSelection(.enabled)
            }
        }
        .foregroundStyle(.orange)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
