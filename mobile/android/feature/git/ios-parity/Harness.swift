import AppKit

// Replays diffs.json through the iOS ParsedDiff and DiffHighlighter and prints what
// they produce: lines, blocks with each run's colors, and every language's rules.

let cases = try! JSONSerialization.jsonObject(
    with: Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1]))
) as! [[String: Any]]

func runs(_ s: NSAttributedString) -> [[Any]] {
    var out: [[Any]] = []
    s.enumerateAttributes(in: NSRange(location: 0, length: s.length)) { attrs, range, _ in
        out.append([
            range.location,
            range.length,
            (attrs[.foregroundColor] as? UIColor)?.desc ?? "",
            (attrs[.backgroundColor] as? UIColor)?.desc ?? "",
        ])
    }
    return out
}

func name(_ kind: DiffLineKind) -> String {
    switch kind {
    case .added: return "added"
    case .removed: return "removed"
    case .hunk: return "hunk"
    case .context: return "context"
    }
}

var results: [[String: Any]] = []
for c in cases {
    let caseName = c["name"] as! String
    let ext = c["ext"] as? String ?? ""
    if let diff = c["diff"] as? String {
        let parsed = ParsedDiff(diff, ext: ext)
        results.append([
            "name": caseName,
            "added": parsed.addedCount,
            "removed": parsed.removedCount,
            "prompt": ParsedDiff.promptDiff(diff),
            "lines": parsed.lines.map { line -> [String: Any] in
                [
                    "id": line.id, "text": line.text, "kind": name(line.kind),
                    "hunkLabel": line.hunkLabel, "indentHint": line.indentHint,
                    "old": line.oldNumber as Any? ?? NSNull(),
                    "new": line.newNumber as Any? ?? NSNull(),
                ]
            },
            "blocks": parsed.blocks.map { block -> [String: Any] in
                [
                    "id": block.id, "kind": block.kind == .hunk ? "hunk" : "code",
                    "hunkLabel": block.hunkLabel, "hunkContext": block.hunkContext,
                    "indentHint": block.indentHint, "lineCount": block.lineCount,
                    "rows": block.rows.map(name),
                    "gutter": block.gutter.string, "gutterRuns": runs(block.gutter),
                    "code": block.code.string, "codeRuns": runs(block.code),
                ]
            },
        ])
    } else {
        var highlighter = DiffHighlighter(ext: ext)
        let lines = c["lines"] as! [String]
        results.append([
            "name": caseName,
            "highlight": lines.map { line -> [String: Any] in
                let out = highlighter.highlight(line)
                return ["text": out.string, "runs": runs(out)]
            },
        ])
    }
}

let exts = ["swift", "rs", "js", "jsx", "mjs", "cjs", "ts", "tsx", "json", "yaml", "yml", "css",
            "scss", "sass", "less", "html", "htm", "xml", "vue", "svelte", "py", "go", "rb",
            "sh", "bash", "zsh", "fish", "md", "", "TS", "Yml"]
let specs = exts.map { ext -> [String: Any] in
    let spec = LanguageSpec.forExtension(ext)
    return [
        "ext": ext, "keywords": spec.keywords.sorted(), "lineComments": spec.lineComments,
        "blockOpen": spec.blockOpen as Any? ?? NSNull(),
        "blockClose": spec.blockClose as Any? ?? NSNull(),
        "strings": spec.strings.map(String.init).sorted(), "isPlain": spec.isPlain,
        "mapKeys": spec.mapKeys,
    ]
}

let out = try! JSONSerialization.data(
    withJSONObject: ["cases": results, "specs": specs],
    options: [.prettyPrinted, .sortedKeys]
)
FileHandle.standardOutput.write(out)
FileHandle.standardOutput.write("\n".data(using: .utf8)!)
