import SwiftUI

/// A zone as the desktop frames it: its buttons in `rows` rows, filled top to
/// bottom and then across, inside one bordered frame. A zone with layers shows
/// one at a time, switched by the dots under it or a sideways swipe, and a
/// folded zone is one button listing them all.
struct ActionZoneView: View {
    let zone: Zone
    let lists: [ZoneList]
    let project: String
    var compact = false
    var onEdit: ((Action) -> Void)? = nil
    let onRun: (Action) -> Void

    private static let gap: CGFloat = 4
    private var memory: ZoneLayerMemory { .shared }
    private var open: Int { memory.openIndex(project: project, zone: zone.name, lists: lists) }
    private var paged: Bool { lists.count > 1 }

    var body: some View {
        if zone.collapsed { folded } else { frame }
    }

    private var frame: some View {
        let shape = RoundedRectangle(cornerRadius: Self.buttonRadius(compact) + Self.gap, style: .continuous)
        return VStack(spacing: Self.gap) {
            if paged {
                ZonePagerLayout(open: open) {
                    ForEach(Array(lists.enumerated()), id: \.element.id) { index, list in
                        page(list)
                            .opacity(index == open ? 1 : 0.12)
                            .allowsHitTesting(index == open)
                            .accessibilityHidden(index != open)
                            .layoutValue(key: EmptyPage.self, value: list.actions.isEmpty)
                    }
                }
                dots
            } else if let only = lists.first {
                grid(only.actions)
            }
        }
        .padding(Self.gap)
        .background(Color(.secondarySystemGroupedBackground).opacity(0.6), in: shape)
        .overlay(shape.strokeBorder(Color.primary.opacity(0.14)))
        .clipShape(shape)
        .contentShape(shape)
        .simultaneousGesture(swipe, including: paged ? .all : .subviews)
        .accessibilityElement(children: .contain)
        .accessibilityLabel(zone.label)
    }

    @ViewBuilder
    private func page(_ list: ZoneList) -> some View {
        if list.actions.isEmpty {
            Text("No actions")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            grid(list.actions)
        }
    }

    private func grid(_ actions: [Action]) -> some View {
        ZoneGridLayout(rows: zone.rows, spacing: Self.gap) {
            ForEach(actions) { action in
                DesktopActionButton(action: action, project: project, compact: compact, fill: true,
                                    onEdit: onEdit, onRun: onRun)
            }
        }
    }

    /// The open layer's name when any layer has one, then a dot per layer. The
    /// name steps to the next layer, a dot opens its own.
    private var dots: some View {
        HStack(spacing: 6) {
            if lists.contains(where: { $0.layer?.label.isEmpty == false }) {
                Button { show((open + 1) % lists.count) } label: {
                    ZStack {
                        ForEach(Array(lists.enumerated()), id: \.element.id) { index, list in
                            Text(list.title).opacity(index == open ? 1 : 0)
                        }
                    }
                    .font(.caption2.weight(.medium))
                    .foregroundStyle(.secondary)
                    .frame(height: 20)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
            HStack(spacing: 0) {
                ForEach(Array(lists.enumerated()), id: \.element.id) { index, list in
                    Button { show(index) } label: {
                        Circle()
                            .fill(index == open ? Color.accentColor : Color.primary.opacity(0.26))
                            .frame(width: 6, height: 6)
                            .frame(width: 16, height: 20)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Layer")
        .accessibilityValue(lists[open].title)
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment: show(open + 1)
            case .decrement: show(open - 1)
            @unknown default: break
            }
        }
    }

    private var swipe: some Gesture {
        DragGesture(minimumDistance: 24)
            .onEnded { value in
                let dx = value.translation.width
                guard abs(dx) > 40, abs(dx) > abs(value.translation.height) else { return }
                show(open + (dx < 0 ? 1 : -1))
            }
    }

    private func show(_ index: Int) {
        guard index != open, lists.indices.contains(index), let layer = lists[index].layer else { return }
        Haptics.tap()
        withAnimation(.smooth(duration: 0.34)) {
            memory.show(layer.name, project: project, zone: zone.name)
        }
    }

    /// Folded on the desktop: the zone's name on one button whose menu holds
    /// its buttons, a section per layer.
    private var folded: some View {
        Menu {
            if paged {
                ForEach(lists.filter { !$0.actions.isEmpty }) { list in
                    Section(list.title) {
                        ActionMenuItems(actions: list.actions, onPick: onRun)
                    }
                }
            } else {
                ActionMenuItems(actions: lists.first?.actions ?? [], onPick: onRun)
            }
        } label: {
            HStack(spacing: 6) {
                Text(zone.label).lineLimit(1)
                Image(systemName: "chevron.down")
                    .font(.system(size: 11, weight: .bold))
            }
            .padding(.horizontal, 14)
            .desktopActionFace(nil, compact: compact)
        }
        .menuStyle(.button)
        .buttonStyle(.plain)
        .accessibilityLabel(zone.label)
    }

    /// The corner of the button face inside, so the frame's corner sits
    /// concentric with it.
    private static func buttonRadius(_ compact: Bool) -> CGFloat {
        if #available(iOS 26.0, *) { return 22 }
        return compact ? 10 : 12
    }
}

/// A zone's buttons in `rows` rows, filled top to bottom and then across, each
/// as wide as the widest in its column. A column that no longer fits the width
/// starts a new band of rows below, so a zone never runs off the screen.
private struct ZoneGridLayout: Layout {
    var rows: Int
    var spacing: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        arrange(subviews, width: proposal.width ?? .infinity).size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        for (view, frame) in zip(subviews, arrange(subviews, width: bounds.width).frames) {
            view.place(at: CGPoint(x: bounds.minX + frame.minX, y: bounds.minY + frame.minY),
                       proposal: ProposedViewSize(frame.size))
        }
    }

    private func arrange(_ subviews: Subviews, width: CGFloat) -> (frames: [CGRect], size: CGSize) {
        let sizes = subviews.map { $0.sizeThatFits(.unspecified) }
        let rows = max(rows, 1)
        let rowHeight = sizes.map(\.height).max() ?? 0
        var frames = [CGRect](repeating: .zero, count: sizes.count)
        var origin = CGPoint.zero
        var bandRows = 0
        var maxX: CGFloat = 0
        for start in stride(from: 0, to: sizes.count, by: rows) {
            let column = start..<min(start + rows, sizes.count)
            let columnWidth = min(column.map { sizes[$0].width }.max() ?? 0, width)
            if origin.x > 0 && origin.x + columnWidth > width {
                origin = CGPoint(x: 0, y: origin.y + CGFloat(bandRows) * (rowHeight + spacing))
                bandRows = 0
            }
            for (row, index) in column.enumerated() {
                frames[index] = CGRect(x: origin.x, y: origin.y + CGFloat(row) * (rowHeight + spacing),
                                       width: columnWidth, height: rowHeight)
            }
            bandRows = max(bandRows, column.count)
            maxX = max(maxX, origin.x + columnWidth)
            origin.x += columnWidth + spacing
        }
        let height = origin.y + CGFloat(bandRows) * (rowHeight + spacing) - (bandRows > 0 ? spacing : 0)
        return (frames, CGSize(width: maxX, height: height))
    }
}

/// An empty layer takes whatever room the others leave it.
private struct EmptyPage: LayoutValueKey {
    static let defaultValue = false
}

/// Every layer in one spot as big as the biggest, the open one in view and the
/// others lined up beside it, so opening another slides them sideways.
private struct ZonePagerLayout: Layout {
    var open: Int

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        subviews.filter { !$0[EmptyPage.self] }.reduce(CGSize.zero) { size, view in
            let page = view.sizeThatFits(ProposedViewSize(width: proposal.width, height: nil))
            return CGSize(width: max(size.width, page.width), height: max(size.height, page.height))
        }
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        for (index, view) in subviews.enumerated() {
            let x = bounds.minX + CGFloat(index - open) * (bounds.width + 16)
            view.place(at: CGPoint(x: x, y: bounds.minY), proposal: ProposedViewSize(bounds.size))
        }
    }
}
