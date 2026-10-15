import SwiftUI

/// The project screen's open tabs, each with its swipe actions and long-press
/// menu, then New tab. Close only asks for it: the screen confirms first, since
/// whatever runs in the tab stops with it.
struct ProjectTabsSection: View {
    @Environment(AppModel.self) private var model
    let project: String
    let terminals: [TerminalInfo]
    let agents: [String: ProjectAgentRow]
    let creating: Bool
    @Binding var renaming: TabRename?
    @Binding var closing: TerminalInfo?
    let onOpen: (TerminalInfo) -> Void

    var body: some View {
        Section {
            ForEach(terminals) { t in
                TabRow(term: t, agent: agents[t.id], onOpen: { onOpen(t) })
                    .tabMenu(term: t, agent: agents[t.id]) {
                        pinButton(t)
                        renameButton(t)
                        closeButton(t)
                    }
                    .compactRow()
                    .swipeActions(edge: .leading, allowsFullSwipe: true) {
                        pinButton(t).tint(.orange)
                    }
                    // No full swipe: a stray swipe mustn't be enough to end a
                    // tab's process.
                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                        // Not a destructive role: that slides the row away before
                        // the close is confirmed.
                        Button {
                            Haptics.warning()
                            closing = t
                        } label: {
                            Label("Close", systemImage: "xmark")
                        }
                        .tint(.red)
                        renameButton(t).tint(.gray)
                    }
            }
            .onMove(perform: move)
            if creating {
                TerminalRowSkeleton()
                    .compactRow()
            }
            Button {
                model.newTerminal(project)
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "plus")
                    Text("New tab")
                }
            }
            .disabled(creating)
            .compactRow()
        } header: {
            SectionTitle(text: "Tabs")
        }
    }

    private func pinButton(_ t: TerminalInfo) -> some View {
        Button {
            model.pinTerminal(project, id: t.id)
        } label: {
            Label(t.pinned ? "Unpin" : "Pin", systemImage: t.pinned ? "pin.slash" : "pin")
        }
    }

    private func renameButton(_ t: TerminalInfo) -> some View {
        Button {
            renaming = TabRename(t)
        } label: {
            Label("Rename", systemImage: "pencil")
        }
    }

    private func closeButton(_ t: TerminalInfo) -> some View {
        Button(role: .destructive) {
            Haptics.warning()
            closing = t
        } label: {
            Label("Close", systemImage: "xmark")
        }
    }

    private func move(from source: IndexSet, to destination: Int) {
        var order = terminals.map(\.id)
        order.move(fromOffsets: source, toOffset: destination)
        model.reorderTerminals(project, order: order)
    }
}

/// The Tabs section while its list isn't known yet: placeholder rows instead of
/// an empty list someone could take for "no tabs".
struct ProjectTabsLoadingSection: View {
    var body: some View {
        Section {
            ForEach(0..<3, id: \.self) { _ in
                TerminalRowSkeleton()
                    .compactRow()
            }
        } header: {
            SectionTitle(text: "Tabs")
        }
    }
}
