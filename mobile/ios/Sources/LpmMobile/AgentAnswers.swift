import Foundation

/// One answer a terminal's agent gave, as the raw markdown it wrote, so a copy
/// keeps tables and code fences. `at` is unix millis, nil when the transcript
/// record carried no timestamp.
struct RecentAnswer {
    let text: String
    let at: Int?

    init(_ o: [String: Any]) {
        text = o["text"] as? String ?? ""
        at = o["at"] as? Int
    }

    /// The first meaningful line, stripped of markdown furniture, so a row reads
    /// like a sentence rather than a heading marker. Mirrors the desktop's
    /// answerPreview.
    var preview: String {
        for line in text.split(separator: "\n") {
            let plain = line.drop { " \t#>*`-".contains($0) }.trimmingCharacters(in: .whitespaces)
            if !plain.isEmpty { return plain }
        }
        return text.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

extension Wire {
    static func answers(id: String, project: String, limit: Int, reqId: String) -> String {
        json(["t": "answers", "id": id, "project": project, "limit": limit, "reqId": reqId])
    }
}

enum AnswerRequestError: LocalizedError {
    case mac(String)
    case timedOut
    case disconnected

    var errorDescription: String? {
        switch self {
        case .mac(let message): return message
        case .timedOut: return "The Mac didn't answer in time."
        case .disconnected: return "Lost the connection to the Mac."
        }
    }
}

/// Matches each `answers` reply to the copy waiting on it. The Mac reads
/// transcripts on a worker, so replies can arrive out of order and are paired by
/// `reqId`, not by arrival.
@MainActor
final class AnswerRequests {
    private static let timeout: Duration = .seconds(10)
    private var waiting: [String: CheckedContinuation<[RecentAnswer], Error>] = [:]

    func request(_ send: (String) -> Void) async throws -> [RecentAnswer] {
        let reqId = UUID().uuidString
        return try await withCheckedThrowingContinuation { continuation in
            waiting[reqId] = continuation
            send(reqId)
            Task { [weak self] in
                try? await Task.sleep(for: Self.timeout)
                self?.finish(reqId, .failure(AnswerRequestError.timedOut))
            }
        }
    }

    func deliver(reqId: String, answers: [RecentAnswer]?, error: String?) {
        finish(reqId, answers.map { .success($0) } ?? .failure(AnswerRequestError.mac(error ?? "")))
    }

    func failAll() {
        for reqId in Array(waiting.keys) {
            finish(reqId, .failure(AnswerRequestError.disconnected))
        }
    }

    private func finish(_ reqId: String, _ result: Result<[RecentAnswer], Error>) {
        waiting.removeValue(forKey: reqId)?.resume(with: result)
    }
}
