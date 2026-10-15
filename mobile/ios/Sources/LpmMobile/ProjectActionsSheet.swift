import SwiftUI

/// Every project action as the desktop shows them: the header row and the footer
/// row, each with its zones framed among the buttons, and the menu-only list.
/// Picking one hands it back to the host, which runs it once the sheet is gone;
/// + adds an action and a long-press edits one.
struct ProjectActionsSheet: View {
    let project: Project
    let onPick: (Action) -> Void
    @Environment(AppModel.self) private var model
    @State private var editor: ActionEditorContext?

    var body: some View {
        let rows = ActionRows(actions: project.actions, zones: project.zones)
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    section("Header", rows.header, compact: false)
                    section("Footer", rows.footer, compact: true)
                    section("Menu", rows.menu.map(ActionRowItem.action), compact: false)
                }
                .padding(20)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("Actions")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        editor = .create
                    } label: {
                        Image(systemName: "plus")
                    }
                    .accessibilityLabel("New Action")
                }
            }
        }
        .presentationDetents([.medium, .large])
        .sheet(item: $editor) { context in
            ActionEditorSheet(project: project, context: context).environment(model)
        }
    }

    @ViewBuilder
    private func section(_ title: String, _ items: [ActionRowItem], compact: Bool) -> some View {
        if !items.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                Text(title)
                    .textCase(.uppercase)
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.secondary)
                FlowLayout(spacing: 8) {
                    ForEach(items) { item in
                        switch item {
                        case .action(let action):
                            DesktopActionButton(action: action, project: project.name, compact: compact,
                                                onEdit: edit, onRun: onPick)
                        case .zone(let zone, let lists):
                            ActionZoneView(zone: zone, lists: lists, project: project.name, compact: compact,
                                           onEdit: edit, onRun: onPick)
                        }
                    }
                }
            }
        }
    }

    private func edit(_ action: Action) {
        editor = .edit(key: action.name)
    }
}

/// Lays its children out left to right, wrapping onto a new row when one runs out
/// of width.
private struct FlowLayout: Layout {
    var spacing: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.replacingUnspecifiedDimensions().width
        return CGSize(width: width, height: frames(for: subviews, width: width).map(\.maxY).max() ?? 0)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        for (view, frame) in zip(subviews, frames(for: subviews, width: bounds.width)) {
            view.place(at: CGPoint(x: bounds.minX + frame.minX, y: bounds.minY + frame.minY),
                       proposal: ProposedViewSize(frame.size))
        }
    }

    private func frames(for subviews: Subviews, width: CGFloat) -> [CGRect] {
        var frames: [CGRect] = []
        var origin = CGPoint.zero
        var rowHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(ProposedViewSize(width: width, height: nil))
            if origin.x > 0 && origin.x + size.width > width {
                origin = CGPoint(x: 0, y: origin.y + rowHeight + spacing)
                rowHeight = 0
            }
            frames.append(CGRect(origin: origin, size: size))
            origin.x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
        return frames
    }
}
