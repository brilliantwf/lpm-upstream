import Foundation

// Reads a JSON array of markdown documents and prints, one JSON line per document,
// the segments iOS's SpeechText would speak.
let input = try! Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1]))
let documents = try! JSONSerialization.jsonObject(with: input) as! [String]
for markdown in documents {
    let segments = SpeechText.segments(markdown).map { segment -> [String: Any] in
        ["block": segment.blockID, "text": segment.text, "pauseMs": Int((segment.pause * 1000).rounded())]
    }
    let line = try! JSONSerialization.data(withJSONObject: ["markdown": markdown, "segments": segments],
                                           options: [.sortedKeys, .withoutEscapingSlashes])
    print(String(data: line, encoding: .utf8)!)
}
