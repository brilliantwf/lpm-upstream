import SwiftUI

/// The commit sheet, the phone's take on the desktop's Commit dialog: the message
/// with an AI "Generate message", every changed file to include or leave out, and
/// Commit. The message and selection belong to the review screen, so closing the
/// sheet keeps the draft; a successful commit closes it.
struct GitCommitSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let project: String
    @Binding var message: String
    @Binding var deselected: Set<String>

    private var files: [GitFile] { model.git.snapshots[project]?.files ?? [] }
    private var selectedPaths: [String] { files.map(\.path).filter { !deselected.contains($0) } }
    private var committing: Bool { model.git.committing.contains(project) }
    private var generating: Bool { model.git.generatingMessage.contains(project) }
    private var trimmedMessage: String { message.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var canCommit: Bool {
        !selectedPaths.isEmpty && !trimmedMessage.isEmpty && !committing && !model.git.pushing.contains(project)
    }

    var body: some View {
        NavigationStack {
            Form {
                if let notice = conflictNotice {
                    Section {
                        Label(notice, systemImage: "exclamationmark.triangle.fill")
                            .font(.footnote)
                            .foregroundStyle(.red)
                    }
                }
                Section {
                    TextField("Commit message", text: $message, axis: .vertical)
                        .lineLimit(3...8)
                    Button {
                        model.git.genMessage(project, files: selectedPaths)
                    } label: {
                        HStack(spacing: 8) {
                            if generating {
                                ProgressView().controlSize(.small)
                            } else {
                                Image(systemName: "sparkles")
                            }
                            Text(generating ? "Generating…" : "Generate message")
                        }
                    }
                    .disabled(generating || selectedPaths.isEmpty)
                }
                Section {
                    ForEach(files) { file in
                        CommitFileRow(project: project, file: file, selected: !deselected.contains(file.path)) {
                            toggle(file.path)
                        }
                    }
                } header: {
                    HStack {
                        Text("Files")
                        Spacer()
                        Button(selectedPaths.count == files.count ? "Deselect All" : "Select All") {
                            if selectedPaths.count == files.count {
                                deselected = Set(files.map(\.path))
                            } else {
                                deselected = []
                            }
                        }
                        .font(.subheadline)
                        .disabled(files.isEmpty)
                    }
                } footer: {
                    Text("Only the selected files are committed.")
                }
            }
            .navigationTitle("Commit")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(action: commit) {
                        if committing {
                            ProgressView().controlSize(.small)
                        } else {
                            Text("Commit").fontWeight(.semibold)
                        }
                    }
                    .disabled(!canCommit)
                }
            }
            .onChange(of: model.git.commitTick[project]) { _, _ in dismiss() }
            // The review screen holds its own alert back while this sheet is up, so
            // a failed commit or message generation is reported here.
            .alert(
                model.git.opError[project]?.title ?? "",
                isPresented: Binding(get: { model.git.opError[project] != nil },
                                     set: { if !$0 { model.git.opError[project] = nil } })
            ) {
                Button("OK", role: .cancel) { model.git.opError[project] = nil }
            } message: {
                Text(model.git.opError[project]?.message ?? "")
            }
        }
    }

    /// The desktop's ConflictNotice: which files still have merge conflicts.
    private var conflictNotice: String? {
        let paths = files.filter { $0.status == "conflicted" }.map(\.path)
        if paths.isEmpty { return nil }
        let shown = 3
        let named = paths.count > shown
            ? paths.prefix(shown).joined(separator: ", ") + " and \(paths.count - shown) more"
            : paths.joined(separator: ", ")
        return "\(named) \(paths.count == 1 ? "has" : "have") merge conflicts. Resolve the conflict markers (<<<<<<< … >>>>>>>) before committing."
    }

    private func toggle(_ path: String) {
        if deselected.contains(path) { deselected.remove(path) } else { deselected.insert(path) }
    }

    private func commit() {
        model.git.commit(project, message: trimmedMessage, files: selectedPaths)
    }
}

/// A file in the commit sheet: its checkmark, status letter, name over folder, and
/// +N −M. The whole row includes or leaves it out.
private struct CommitFileRow: View {
    @Environment(AppModel.self) private var model
    let project: String
    let file: GitFile
    let selected: Bool
    let toggle: () -> Void

    var body: some View {
        Button(action: toggle) {
            HStack(spacing: 12) {
                Image(systemName: selected ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 22))
                    .foregroundStyle(selected ? Color.accentColor : Color.secondary)
                GitStatusBadge(status: file.status)
                GitFileName(file: file)
                    .foregroundStyle(.primary)
                Spacer(minLength: 8)
                if let parsed = model.git.parsedDiff(project, path: file.path) {
                    DiffStat(added: parsed.addedCount, removed: parsed.removedCount)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(file.path)
        .accessibilityValue(selected ? "Included" : "Excluded")
        .accessibilityAddTraits(.isButton)
    }
}
