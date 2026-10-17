import SwiftUI
import UIKit

/// The session's recent answers, oldest at the top and newest at the bottom by
/// the thumb, like the desktop's pick-list. Fetched fresh each time it opens; a
/// tap copies that answer's original markdown and closes the sheet.
struct CopyAnswerSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let source: AnswerSource
    let onCopied: () -> Void

    private static let listLimit = 20
    private static let relativeFormatter: RelativeDateTimeFormatter = {
        let f = RelativeDateTimeFormatter()
        f.unitsStyle = .full
        return f
    }()

    @State private var answers: [RecentAnswer]?
    @State private var error: String?

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Copy an Answer")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") { dismiss() }
                    }
                }
        }
        .presentationDetents([.medium, .large])
        .task { await load() }
    }

    @ViewBuilder private var content: some View {
        if let answers, answers.isEmpty {
            ContentUnavailableView("No Answers Yet", systemImage: "text.bubble",
                                   description: Text("Answers show up here once the agent replies."))
        } else if let answers {
            ScrollViewReader { proxy in
                List(Array(answers.enumerated()), id: \.offset) { index, answer in
                    Button { copy(answer) } label: { row(answer, newest: index == answers.count - 1) }
                        .id(index)
                }
                .onAppear { proxy.scrollTo(answers.count - 1, anchor: .bottom) }
            }
        } else if let error {
            ContentUnavailableView("Couldn't Load Answers", systemImage: "exclamationmark.triangle",
                                   description: Text(error))
        } else {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private func row(_ answer: RecentAnswer, newest: Bool) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            VStack(alignment: .leading, spacing: 3) {
                Text(answer.preview)
                    .font(.subheadline)
                    .foregroundStyle(.primary)
                    .lineLimit(2)
                if let at = answer.at {
                    Text(Self.relativeFormatter.localizedString(
                        for: Date(timeIntervalSince1970: Double(at) / 1000), relativeTo: Date()))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer(minLength: 0)
            Image(systemName: "doc.on.doc")
                .font(.footnote)
                .foregroundStyle(newest ? SwiftUI.Color.blue : SwiftUI.Color.secondary)
        }
        .contentShape(Rectangle())
    }

    private func load() async {
        do {
            let newestFirst = try await model.recentAnswers(source, limit: Self.listLimit)
            answers = Array(newestFirst.reversed())
        } catch {
            self.error = error.localizedDescription
        }
    }

    private func copy(_ answer: RecentAnswer) {
        UIPasteboard.general.string = answer.text
        Haptics.success()
        onCopied()
        dismiss()
    }
}
