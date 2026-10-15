import SwiftUI

/// Whether the action editor is creating a new action or editing an existing
/// one. Edit carries the action's YAML key so the sheet can fetch its body.
enum ActionEditorContext: Identifiable {
    case create
    case edit(key: String)

    var id: String {
        switch self {
        case .create: return "create"
        case .edit(let key): return "edit:\(key)"
        }
    }

    var isEditing: Bool { if case .edit = self { return true } else { return false } }
    var editingKey: String? { if case .edit(let key) = self { return key } else { return nil } }
}

/// Whether the action is a single command button or a menu of sub-actions.
private enum ActionKind: String, CaseIterable, Identifiable {
    case command, menu
    var id: String { rawValue }
    var title: String { self == .command ? "Command button" : "Menu of sub-actions" }
}

/// Where an action runs. Maps to the YAML `type` field (a plain pop-up run omits
/// it, matching the backend default).
private enum ActionRunIn: String, CaseIterable, Identifiable {
    case popup, terminal, command, background
    var id: String { rawValue }

    var typeValue: String? {
        switch self {
        case .popup: return nil
        case .terminal: return "terminal"
        case .command: return "command"
        case .background: return "background"
        }
    }

    var title: String {
        switch self {
        case .popup: return "Pop-up window"
        case .terminal: return "New terminal tab"
        case .command: return "Current terminal"
        case .background: return "In the background"
        }
    }

    static func from(_ type: String?) -> ActionRunIn {
        switch type {
        case "terminal": return .terminal
        case "command": return .command
        case "background": return .background
        default: return .popup
        }
    }
}

private struct ChildDraft: Identifiable {
    let id = UUID()
    /// The item's YAML key on disk, kept so `primary` and saved choices still
    /// point at it after its label changes. Empty for a new item.
    var key = ""
    var label = ""
    var cmd = ""
    var runIn: ActionRunIn = .terminal
}

/// What tapping a menu action's button does: open the menu, or run one item
/// (the last one used, or a chosen one) with the rest behind a chevron.
private enum PrimaryChoice: Hashable {
    case menu, lastUsed, child(UUID)
}

private struct ActionDraft {
    var name = ""
    var label = ""
    var emoji = ""
    var kind: ActionKind = .command
    var cmd = ""
    var cwd = ""
    var runIn: ActionRunIn = .terminal
    var reuse = false
    var confirm = false
    var placement: ActionPlacement = .header
    /// Where the action sat on disk. An edit that keeps it there leaves
    /// `display` and `layer` as written; a move writes both, the top bar as an
    /// explicit `header`, since it overrides a footer or zone display from
    /// another file.
    var seededPlacement: ActionPlacement = .header
    var inputs: [ActionInputDraft] = []
    var children: [ChildDraft] = []
    var color = ""
    var primary: PrimaryChoice = .menu
}

/// Create or edit a project action. Presented as a sheet from ProjectConfigView.
/// On save it hands the built action mapping to the Mac (see AppModel.saveAction),
/// which writes it to the same layer file a desktop edit would. Edit mode starts
/// the payload from the action's on-disk body so fields this form doesn't surface
/// (keyboard shortcut, port, position, …) survive the round-trip.
struct ActionEditorSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    let project: Project
    let context: ActionEditorContext

    @State private var draft = ActionDraft()
    @State private var seedBody: [String: Any]?
    @State private var seedSection: String?
    @State private var seedSource = "project"
    @State private var seeded = false
    @State private var didRequest = false
    @State private var confirmDelete = false
    @State private var pendingToken: Int?
    @FocusState private var cwdFocused: Bool

    private var editingKey: String? { context.editingKey }

    private var otherActionKeys: [String] {
        project.actions.map(\.name).filter { $0 != editingKey }
    }

    private var loading: Bool {
        context.isEditing && !seeded && model.actionBodyError == nil
    }

    private var validationError: String? {
        let key = draft.name.trimmed
        if key.isEmpty { return "Give this action a name." }
        if key.contains(":") { return "Names can't contain a colon." }
        if otherActionKeys.contains(key) { return "Another action already uses that name." }
        switch draft.kind {
        case .command:
            if draft.cmd.trimmed.isEmpty { return "Enter a command to run." }
            if let problem = ActionInputsYaml.problem(draft.inputs) { return problem }
        case .menu:
            if !draft.children.contains(where: { !$0.cmd.trimmed.isEmpty }) {
                return "Add at least one item with a command."
            }
        }
        return nil
    }

    private var canSave: Bool {
        validationError == nil && !loading && !model.configMutationInFlight
    }

    var body: some View {
        NavigationStack {
            Group {
                if loading {
                    ProgressView("Loading action…")
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    form
                }
            }
            .navigationTitle(context.isEditing ? "Edit Action" : "Add Action")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if model.configMutationInFlight {
                        ProgressView()
                    } else {
                        Button("Save") { submit() }
                            .fontWeight(.semibold)
                            .disabled(!canSave)
                    }
                }
            }
        }
        .interactiveDismissDisabled(model.configMutationInFlight)
        .onAppear(perform: seedIfReady)
        .onChange(of: model.actionBody == nil) { _, _ in seedIfReady() }
        .onChange(of: model.configMutationDoneToken) { _, token in
            if let pending = pendingToken, token > pending {
                Haptics.success()
                dismiss()
            }
        }
        .alert("Couldn't save action", isPresented: mutationErrorPresented) {
            Button("OK", role: .cancel) { model.configMutationError = nil }
        } message: {
            Text(model.configMutationError ?? "")
        }
        .alert("Couldn't load action", isPresented: bodyErrorPresented) {
            Button("OK", role: .cancel) { model.actionBodyError = nil; dismiss() }
        } message: {
            Text(model.actionBodyError ?? "")
        }
        .confirmationDialog("Delete this action?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Delete", role: .destructive) { remove() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Remove it from this project. This can't be undone.")
        }
    }

    private var form: some View {
        Form {
            basicsSection
            kindSection
            if draft.kind == .command {
                commandSection
                ActionInputsSection(inputs: $draft.inputs)
            } else {
                childrenSection
                primarySection
            }
            placementSection
            Section("Color") {
                ActionColorPicker(color: $draft.color)
            }
            if context.isEditing { deleteSection }
        }
    }

    // MARK: sections

    private var basicsSection: some View {
        Section {
            TextField("Name", text: $draft.name)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
            TextField("Label (optional)", text: $draft.label)
            TextField("Emoji (optional)", text: $draft.emoji)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
                .onChange(of: draft.emoji) { _, v in
                    if let first = v.first, v.count > 1 { draft.emoji = String(first) }
                }
        } footer: {
            Text("The name is how this action is stored. The label is what you see on the button.")
        }
    }

    private var kindSection: some View {
        Section {
            Picker("Type", selection: $draft.kind) {
                ForEach(ActionKind.allCases) { Text($0.title).tag($0) }
            }
        }
    }

    @ViewBuilder private var commandSection: some View {
        Section("Command") {
            TextField("npm run deploy", text: $draft.cmd, axis: .vertical)
                .lineLimit(1...4)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
                .font(.system(.body, design: .monospaced))
        }

        Section {
            LabeledContent("Working directory") {
                TextField("Working directory", text: $draft.cwd, prompt: Text("./backend"))
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .font(.system(.callout, design: .monospaced))
                    .multilineTextAlignment(.trailing)
                    .focused($cwdFocused)
            }
            .contentShape(Rectangle())
            .onTapGesture { cwdFocused = true }
            Picker("Runs in", selection: $draft.runIn) {
                ForEach(ActionRunIn.allCases) { Text($0.title).tag($0) }
            }
            if draft.runIn == .terminal {
                Toggle("Reuse the same tab", isOn: $draft.reuse)
            }
            Toggle("Ask before running", isOn: $draft.confirm)
        } footer: {
            Text(runInHint)
        }
    }

    private var childrenSection: some View {
        Group {
            ForEach($draft.children) { $child in
                Section {
                    TextField("Label", text: $child.label)
                    TextField("Command", text: $child.cmd, axis: .vertical)
                        .lineLimit(1...4)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                        .font(.system(.callout, design: .monospaced))
                    Picker("Runs in", selection: $child.runIn) {
                        ForEach(ActionRunIn.allCases) { Text($0.title).tag($0) }
                    }
                    Button("Remove item", role: .destructive) {
                        draft.children.removeAll { $0.id == child.id }
                    }
                } header: {
                    Text(child.label.trimmed.isEmpty ? "Menu item" : child.label.trimmed)
                }
            }
            Section {
                Button {
                    draft.children.append(ChildDraft())
                } label: {
                    Label("Add item", systemImage: "plus")
                }
            } footer: {
                Text("Each item runs its own command from the menu.")
            }
        }
    }

    private var primarySection: some View {
        Section {
            Picker("Button runs", selection: $draft.primary) {
                Text("Nothing, opens the menu").tag(PrimaryChoice.menu)
                Text("Last used item").tag(PrimaryChoice.lastUsed)
                ForEach(draft.children.filter { !$0.cmd.trimmed.isEmpty }) { child in
                    Text(child.label.trimmed.isEmpty ? "Menu item" : child.label.trimmed)
                        .tag(PrimaryChoice.child(child.id))
                }
            }
        } footer: {
            Text("A split button runs that item with one tap; its arrow opens the rest.")
        }
    }

    private var placementSection: some View {
        Section {
            Picker("Placement", selection: $draft.placement) {
                ForEach(placementOptions) { Text($0.title(in: project.zones)).tag($0) }
            }
        } header: {
            Text("Placement")
        } footer: {
            if let hint = draft.placement.hint(in: project.zones) { Text(hint) }
        }
    }

    private var placementOptions: [ActionPlacement] {
        ActionPlacement.options(zones: project.zones, source: seedSource, current: draft.seededPlacement)
    }

    private var deleteSection: some View {
        Section {
            Button(role: .destructive) {
                Haptics.warning()
                confirmDelete = true
            } label: {
                HStack {
                    Spacer()
                    Text("Delete action")
                    Spacer()
                }
            }
            .disabled(model.configMutationInFlight)
        }
    }

    private var runInHint: String {
        switch draft.runIn {
        case .popup: return "Runs once and shows the output in a pop-up."
        case .terminal: return draft.reuse
            ? "Reuses the same terminal each time."
            : "Opens a new terminal each time."
        case .command: return "Runs in the terminal you're currently using."
        case .background: return "Runs quietly and notifies you when it's done."
        }
    }

    // MARK: derived

    private var mutationErrorPresented: Binding<Bool> {
        Binding(get: { model.configMutationError != nil },
                set: { if !$0 { model.configMutationError = nil } })
    }

    private var bodyErrorPresented: Binding<Bool> {
        Binding(get: { model.actionBodyError != nil },
                set: { if !$0 { model.actionBodyError = nil } })
    }

    // MARK: lifecycle

    private func seedIfReady() {
        guard !seeded else { return }
        switch context {
        case .create:
            model.actionBody = nil
            seeded = true
        case .edit(let key):
            if !didRequest {
                didRequest = true
                model.loadActionBody(project: project.name, key: key)
                return
            }
            if let body = model.actionBody {
                seedBody = body
                seedSection = model.actionBodySection
                if !model.actionBodySource.isEmpty { seedSource = model.actionBodySource }
                seed(from: body, key: key)
                seeded = true
            }
        }
    }

    private func seed(from body: [String: Any], key: String) {
        var d = ActionDraft()
        d.name = key
        if let s = body["label"] as? String { d.label = s }
        if let s = body["emoji"] as? String { d.emoji = s }
        if let s = body["cmd"] as? String { d.cmd = s }
        if let s = body["cwd"] as? String { d.cwd = s }
        d.runIn = ActionRunIn.from(body["type"] as? String)
        d.reuse = (body["reuse"] as? Bool) == true
        d.confirm = (body["confirm"] as? Bool) == true
        d.placement = ActionPlacement(display: body["display"] as? String ?? "",
                                      layer: body["layer"] as? String, zones: project.zones)
        d.seededPlacement = d.placement
        d.inputs = ActionInputsYaml.drafts(from: body["inputs"])
        if let children = body["actions"] as? [String: Any], !children.isEmpty {
            d.kind = .menu
            d.children = childDrafts(from: children)
        }
        d.color = body["color"] as? String ?? ""
        let primary = body["primary"] as? String ?? ""
        if primary == Action.lastUsed {
            d.primary = .lastUsed
        } else if let child = d.children.first(where: { $0.key == primary }) {
            d.primary = .child(child.id)
        }
        draft = d
    }

    private func childDrafts(from map: [String: Any]) -> [ChildDraft] {
        map.compactMap { name, value -> ChildDraft? in
            guard let v = value as? [String: Any] else { return nil }
            var child = ChildDraft()
            child.key = name
            child.label = v["label"] as? String ?? name
            child.cmd = v["cmd"] as? String ?? ""
            child.runIn = ActionRunIn.from(v["type"] as? String)
            return child
        }
        .sorted { $0.label.localizedCaseInsensitiveCompare($1.label) == .orderedAscending }
    }

    // MARK: build & submit

    private func buildPayload() -> [String: Any] {
        // Edit mode starts from the on-disk body so unsurfaced fields survive;
        // create starts empty for the cleanest possible YAML.
        var p: [String: Any] = context.isEditing ? (seedBody ?? [:]) : [:]

        setOrRemove(&p, "label", draft.label.trimmed.nilIfEmpty)
        setOrRemove(&p, "emoji", draft.emoji.trimmed.nilIfEmpty)
        if !context.isEditing || draft.placement != draft.seededPlacement {
            setOrRemove(&p, "display", displayValue)
            setOrRemove(&p, "layer", draft.placement.layer)
        }
        setOrRemove(&p, "color", draft.color.nilIfEmpty)

        switch draft.kind {
        case .command:
            p.removeValue(forKey: "primary")
            p["cmd"] = draft.cmd.trimmed
            setOrRemove(&p, "cwd", draft.cwd.trimmed.nilIfEmpty)
            setOrRemove(&p, "type", draft.runIn.typeValue)
            setOrRemove(&p, "reuse", (draft.runIn == .terminal && draft.reuse) ? true : nil)
            setOrRemove(&p, "confirm", draft.confirm ? true : nil)
            p.removeValue(forKey: "actions")
            setOrRemove(&p, "inputs", inputsValue)
        case .menu:
            let (children, keys) = buildChildMap()
            p["actions"] = children
            switch draft.primary {
            case .menu: p.removeValue(forKey: "primary")
            case .lastUsed: p["primary"] = Action.lastUsed
            case .child(let id): setOrRemove(&p, "primary", keys[id])
            }
            for key in ["cmd", "cwd", "type", "reuse", "confirm", "port", "portConflict", "inputs"] {
                p.removeValue(forKey: key)
            }
        }
        return p
    }

    private var displayValue: String? {
        switch draft.placement {
        case .footer: return "footer"
        case .zone(let name, _): return name
        case .header: return context.isEditing ? "header" : nil
        }
    }

    /// The inputs mapping, or an empty one once every input saved before has
    /// been removed: the Mac keeps an `inputs` the payload leaves out.
    private var inputsValue: [String: Any]? {
        if let map = ActionInputsYaml.map(from: draft.inputs) { return map }
        return seedBody?["inputs"] == nil ? nil : [:]
    }

    /// The menu's items keyed for YAML, and the key each draft item got. An item
    /// from disk keeps its key; a new one takes a slug of its label.
    private func buildChildMap() -> ([String: Any], [UUID: String]) {
        var map: [String: Any] = [:]
        var keys: [UUID: String] = [:]
        var used: Set<String> = []
        for (index, child) in draft.children.enumerated() {
            let cmd = child.cmd.trimmed
            if cmd.isEmpty { continue }
            var key = child.key.isEmpty ? slugify(child.label) : child.key
            if key.isEmpty { key = "option-\(index + 1)" }
            key = uniqueKey(key, taken: used)
            used.insert(key)
            keys[child.id] = key
            var payload: [String: Any] = [
                "label": child.label.trimmed.isEmpty ? key : child.label.trimmed,
                "cmd": cmd,
            ]
            if let type = child.runIn.typeValue { payload["type"] = type }
            map[key] = payload
        }
        return (map, keys)
    }

    private func submit() {
        guard canSave else { return }
        Haptics.tap()
        pendingToken = model.configMutationDoneToken
        let key = draft.name.trimmed
        model.saveAction(project: project.name, key: key, payload: buildPayload(),
                        previousKey: editingKey, section: seedSection)
    }

    private func remove() {
        guard let key = editingKey else { return }
        pendingToken = model.configMutationDoneToken
        model.deleteAction(project: project.name, key: key)
    }
}

/// Sets `key` to `value` when non-nil, otherwise removes it — the mobile
/// analogue of the desktop ActionPatch set/remove pair, applied onto the merged
/// payload so cleared fields don't linger. Generic on the element type so an
/// already-optional argument isn't re-wrapped into a non-nil `Any?`.
private func setOrRemove<T>(_ dict: inout [String: Any], _ key: String, _ value: T?) {
    if let value { dict[key] = value } else { dict.removeValue(forKey: key) }
}

private func slugify(_ raw: String) -> String {
    let mapped = raw.lowercased().map { c -> Character in
        c.isLetter || c.isNumber ? c : "-"
    }
    return String(mapped).split(separator: "-").joined(separator: "-")
}

private func uniqueKey(_ base: String, taken: Set<String>) -> String {
    if !taken.contains(base) { return base }
    var n = 2
    while taken.contains("\(base)-\(n)") { n += 1 }
    return "\(base)-\(n)"
}

private extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
