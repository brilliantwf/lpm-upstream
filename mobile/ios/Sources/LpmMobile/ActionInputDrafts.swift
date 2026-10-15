import Foundation

/// What an action input asks for, as the desktop's action wizard names it. The
/// YAML `type` is `text` (the default, left out), `radio` or `password`.
enum ActionInputKind: String, CaseIterable, Identifiable {
    case text, radio, password

    var id: String { rawValue }

    var title: String {
        switch self {
        case .text: return "Text"
        case .radio: return "Choice"
        case .password: return "Secret"
        }
    }

    /// Older phones wrote a choice as `select`.
    init(yaml: String?) {
        switch yaml {
        case "radio", "select": self = .radio
        case "password": self = .password
        default: self = .text
        }
    }
}

struct ActionOptionDraft: Identifiable {
    let id = UUID()
    var value = ""
    var label = ""
}

/// One input of an action being edited on the phone.
struct ActionInputDraft: Identifiable {
    let id = UUID()
    var key = ""
    var label = ""
    var kind: ActionInputKind = .text
    var required = false
    var placeholder = ""
    var defaultValue = ""
    var persist = false
    var options: [ActionOptionDraft] = []

    var trimmedKey: String { key.trimmingCharacters(in: .whitespacesAndNewlines) }

    var optionValues: [String] {
        options.map { $0.value.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
    }
}

/// An action's `inputs` as its config file holds them: a mapping keyed by the
/// input's name, the shape the desktop reads and its wizard writes
/// (`inputsToYamlMap`).
enum ActionInputsYaml {
    /// The `{{key}}` tokens the Mac substitutes allow only these characters.
    private static let keyCharacters = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: "_.-"))

    /// Drafts from an action body's `inputs`, in the order the Mac asks them:
    /// by `position`, then by name. Also reads the `[{key, …}]` list earlier
    /// phones wrote, so an action saved that way still shows its inputs.
    static func drafts(from raw: Any?) -> [ActionInputDraft] {
        if let list = raw as? [[String: Any]] {
            return list.map { draft(key: $0["key"] as? String ?? "", $0) }
        }
        guard let map = raw as? [String: Any] else { return [] }
        let bodies = map.mapValues { $0 as? [String: Any] ?? [:] }
        return ordered(bodies).map { draft(key: $0.key, $0.body) }
    }

    /// Entries of an `inputs` mapping in the Mac's prompt order.
    static func ordered(_ map: [String: [String: Any]]) -> [(key: String, body: [String: Any])] {
        let position = { (key: String) in (map[key]?["position"] as? NSNumber)?.doubleValue }
        let keys = map.keys.sorted { a, b in
            switch (position(a), position(b)) {
            case let (pa?, pb?): return pa == pb ? a < b : pa < pb
            case (_?, nil): return true
            case (nil, _?): return false
            case (nil, nil): return a < b
            }
        }
        return keys.map { ($0, map[$0] ?? [:]) }
    }

    /// The `inputs` mapping for an action body, or nil when no input has a
    /// name. `position` is written only when the order differs from the
    /// alphabetical one the Mac would otherwise apply.
    static func map(from drafts: [ActionInputDraft]) -> [String: Any]? {
        let usable = drafts.filter { !$0.trimmedKey.isEmpty }
        if usable.isEmpty { return nil }
        let keys = usable.map(\.trimmedKey)
        let needsPositions = keys != keys.sorted()
        var out: [String: Any] = [:]
        for (index, input) in usable.enumerated() {
            var body: [String: Any] = [:]
            let label = trim(input.label)
            if !label.isEmpty && label != input.trimmedKey { body["label"] = label }
            if input.kind != .text { body["type"] = input.kind.rawValue }
            if input.required { body["required"] = true }
            let placeholder = trim(input.placeholder)
            if !placeholder.isEmpty && input.kind != .radio { body["placeholder"] = placeholder }
            let fallback = trim(input.defaultValue)
            if !fallback.isEmpty { body["default"] = fallback }
            if input.persist && input.kind != .password { body["persist"] = true }
            if input.kind == .radio {
                let options = input.options.compactMap(optionYaml)
                if !options.isEmpty { body["options"] = options }
            }
            if needsPositions { body["position"] = index + 1 }
            out[input.trimmedKey] = body
        }
        return out
    }

    /// Why these inputs can't be saved yet, mirroring the desktop's checks.
    static func problem(_ drafts: [ActionInputDraft]) -> String? {
        var seen: Set<String> = []
        for input in drafts {
            let key = input.trimmedKey
            if key.isEmpty { continue }
            if !key.unicodeScalars.allSatisfy({ $0.isASCII && keyCharacters.contains($0) }) {
                return "Input names can only use letters, numbers, dots, dashes and underscores."
            }
            if !seen.insert(key).inserted { return "Two inputs are named \(key)." }
            guard input.kind == .radio else { continue }
            let values = input.optionValues
            if values.isEmpty { return "Add at least one choice to \(key)." }
            let fallback = trim(input.defaultValue)
            if !fallback.isEmpty && !values.contains(fallback) {
                return "The starting choice of \(key) is no longer in its list."
            }
        }
        return nil
    }

    private static func draft(key: String, _ o: [String: Any]) -> ActionInputDraft {
        var input = ActionInputDraft()
        input.key = key
        let label = o["label"] as? String ?? ""
        input.label = label == key ? "" : label
        input.kind = ActionInputKind(yaml: o["type"] as? String)
        input.required = (o["required"] as? Bool) == true
        input.placeholder = o["placeholder"] as? String ?? ""
        input.defaultValue = o["default"] as? String ?? ""
        input.persist = (o["persist"] as? Bool) == true
        input.options = (o["options"] as? [Any] ?? []).compactMap(optionDraft)
        return input
    }

    private static func optionDraft(_ raw: Any) -> ActionOptionDraft? {
        var option = ActionOptionDraft()
        if let value = raw as? String {
            option.value = value
        } else if let o = raw as? [String: Any] {
            option.value = o["value"] as? String ?? ""
            let label = o["label"] as? String ?? ""
            option.label = label == option.value ? "" : label
        } else {
            return nil
        }
        return option
    }

    /// A plain value, or `{label, value}` when it's shown under another name —
    /// the desktop reads a mapping only when it has both.
    private static func optionYaml(_ option: ActionOptionDraft) -> Any? {
        let value = trim(option.value)
        if value.isEmpty { return nil }
        let label = trim(option.label)
        return label.isEmpty || label == value ? value : ["label": label, "value": value]
    }

    private static func trim(_ s: String) -> String { s.trimmingCharacters(in: .whitespacesAndNewlines) }
}
