import SwiftUI

/// A tab being renamed: the tab, and the name typed so far.
struct TabRename: Equatable {
    let term: TerminalInfo
    var text: String

    init(_ term: TerminalInfo) {
        self.term = term
        text = term.label
    }
}

private struct TabRenameAlert: ViewModifier {
    @Environment(AppModel.self) private var model
    @Binding var rename: TabRename?

    func body(content: Content) -> some View {
        content.alert("Rename terminal", isPresented: Binding(
            get: { rename != nil },
            set: { if !$0 { rename = nil } }
        )) {
            TextField("Name", text: Binding(get: { rename?.text ?? "" },
                                            set: { rename?.text = $0 }))
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            Button("Cancel", role: .cancel) { rename = nil }
            Button("Rename") {
                if let rename {
                    let name = rename.text.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !name.isEmpty && name != rename.term.label {
                        model.renameTerminal(rename.term.project, id: rename.term.id, label: name)
                    }
                }
                rename = nil
            }
        }
    }
}

extension View {
    /// The prompt for a tab's new name, shown while `rename` is set.
    func tabRenameAlert(_ rename: Binding<TabRename?>) -> some View {
        modifier(TabRenameAlert(rename: rename))
    }
}
