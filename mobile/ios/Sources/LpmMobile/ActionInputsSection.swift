import SwiftUI

/// The action editor's inputs: one section per input, then "Add input". The
/// fields follow the desktop wizard's question settings.
struct ActionInputsSection: View {
    @Binding var inputs: [ActionInputDraft]

    var body: some View {
        Group {
            ForEach(inputs) { input in
                Section {
                    fields(binding(input.id))
                    Button("Remove input", role: .destructive) {
                        inputs.removeAll { $0.id == input.id }
                    }
                } header: {
                    Text(input.trimmedKey.isEmpty ? "Input" : input.trimmedKey)
                }
            }
            Section {
                Button {
                    inputs.append(ActionInputDraft())
                } label: {
                    Label("Add input", systemImage: "plus")
                }
            } footer: {
                if let problem = ActionInputsYaml.problem(inputs) {
                    Text(problem).foregroundStyle(.red)
                } else {
                    Text("Values you're asked for before the action runs, filled into the command.")
                }
            }
        }
    }

    @ViewBuilder private func fields(_ input: Binding<ActionInputDraft>) -> some View {
        TextField("Name", text: input.key)
            .autocorrectionDisabled()
            .textInputAutocapitalization(.never)
            .font(.system(.callout, design: .monospaced))
        TextField("Label (optional)", text: input.label)
        Picker("Kind", selection: input.kind) {
            ForEach(ActionInputKind.allCases) { Text($0.title).tag($0) }
        }
        Toggle("Required", isOn: input.required)
        if input.wrappedValue.kind == .radio {
            ForEach(input.wrappedValue.options) { option in
                let option = binding(option.id, in: input)
                HStack(spacing: 8) {
                    TextField("Value", text: option.value)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                    Divider()
                    TextField("Label (optional)", text: option.label)
                }
            }
            .onDelete { offsets in input.wrappedValue.options.remove(atOffsets: offsets) }
            Button {
                input.wrappedValue.options.append(ActionOptionDraft())
            } label: {
                Label("Add choice", systemImage: "plus")
            }
            Picker("Starts as", selection: input.defaultValue) {
                Text("Nothing selected").tag("")
                ForEach(startChoices(input.wrappedValue), id: \.self) { Text($0).tag($0) }
            }
        } else {
            TextField("Placeholder (optional)", text: input.placeholder)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
            TextField("Default value (optional)", text: input.defaultValue)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
        }
        if input.wrappedValue.kind != .password {
            Toggle("Remember the last answer", isOn: input.persist)
        }
    }

    // Rows are bound by id, not by index: SwiftUI still reads a removed row's
    // fields once, and an index past the end would crash.
    private func binding(_ id: UUID) -> Binding<ActionInputDraft> {
        Binding(get: { inputs.first { $0.id == id } ?? ActionInputDraft() },
                set: { new in
                    if let i = inputs.firstIndex(where: { $0.id == id }) { inputs[i] = new }
                })
    }

    private func binding(_ id: UUID, in input: Binding<ActionInputDraft>) -> Binding<ActionOptionDraft> {
        Binding(get: { input.wrappedValue.options.first { $0.id == id } ?? ActionOptionDraft() },
                set: { new in
                    if let i = input.wrappedValue.options.firstIndex(where: { $0.id == id }) {
                        input.wrappedValue.options[i] = new
                    }
                })
    }

    /// The choices a choice input can start on, plus a saved start that has
    /// since left the list, so the picker still shows what the file holds.
    private func startChoices(_ input: ActionInputDraft) -> [String] {
        var values: [String] = []
        for value in input.optionValues where !values.contains(value) { values.append(value) }
        let current = input.defaultValue.trimmingCharacters(in: .whitespacesAndNewlines)
        if !current.isEmpty && !values.contains(current) { values.append(current) }
        return values
    }
}
