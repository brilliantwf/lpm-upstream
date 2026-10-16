import Foundation

/// Demo handler for the terminal copy key: the answers the scripted Claude
/// session has put on screen so far, newest first, as markdown.
extension DemoServer {
    func registerAnswersHandlers() {
        register("answers") { [weak self] o in
            guard let self else { return }
            let id = o["id"] as? String ?? ""
            let limit = max(1, min(o["limit"] as? Int ?? 1, 20))
            let answers: [[String: Any]] = (self.world.agentAnswers[id] ?? [])
                .suffix(limit)
                .reversed()
                .map { ["text": $0.text, "at": $0.at] }
            let reqId = o["reqId"] ?? ""
            self.pushAfter(0.25) {
                ["t": "answers", "id": id, "reqId": reqId, "ok": true, "answers": answers]
            }
        }
    }

    /// Keep an answer the demo agent just printed, its terminal styling turned
    /// back into the markdown a real transcript holds.
    func demoRecordAnswer(_ id: String, ansi: String) {
        let answer = DemoWorld.AgentAnswer(text: Self.markdown(fromANSI: ansi),
                                           at: Int(Date().timeIntervalSince1970 * 1000))
        world.agentAnswers[id, default: []].append(answer)
    }

    private static func markdown(fromANSI ansi: String) -> String {
        var text = ansi
        if let dot = text.range(of: "⏺") { text = String(text[dot.upperBound...]) }
        text = replacing(text, "\u{1B}\\[1m(.*?)\u{1B}\\[0m", with: "**$1**")
        text = replacing(text, "\u{1B}\\[[0-9;?]*[A-Za-z]", with: "")
        return text
            .replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "  • ", with: "- ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func replacing(_ text: String, _ pattern: String, with template: String) -> String {
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return text }
        return regex.stringByReplacingMatches(in: text, range: NSRange(text.startIndex..., in: text),
                                              withTemplate: template)
    }
}
