import SwiftUI

/// Where the usage rows sit: `gap` lifts them to rest on the bottom edge of a
/// short list, and until it has been measured they stay hidden, so they never
/// show mid-list first and then slide down.
struct UsageRest: Equatable {
    var gap: CGFloat = 0
    var shown = false
}

extension View {
    /// Keeps `rest.gap` at whatever lifts the usage rows — the list's last
    /// section — to rest on the bottom edge while everything fits on screen, and
    /// at zero once the list scrolls, so they sit at the foot of a short list and
    /// follow a long one. The measured content already holds the current gap, so
    /// the next one is the old gap plus whatever room is still free.
    ///
    /// `present` says whether the list holds the rows at all; `ready` whether the
    /// rest of the screen has loaded, since a list still filling in moves them
    /// again. They show once both hold and a measurement agrees with the gap.
    /// iOS 17 can't read a list's scroll geometry, so there they show at once and
    /// simply follow the list.
    ///
    /// Pull-to-refresh goes through here too: the refresh spinner borrows room
    /// at the top while it shows, and that isn't room the list lost.
    func restsAtBottom(_ rest: Binding<UsageRest>, present: Bool, ready: Bool,
                       onRefresh: @escaping () async -> Void) -> some View {
        modifier(RestsAtBottom(rest: rest, present: present, ready: ready, refresh: onRefresh))
    }

    /// Reads the meters, today's spend and the Claude accounts the usage rows
    /// need while this list is on screen, again whenever the link comes back,
    /// and refreshes the spend every three minutes as the desktop does.
    func feedsUsageRows() -> some View {
        modifier(FeedsUsageRows())
    }
}

private struct RestsAtBottom: ViewModifier {
    @Binding var rest: UsageRest
    let present: Bool
    let ready: Bool
    let refresh: () async -> Void
    @State private var refreshing = false
    @State private var measured = Measured()

    private final class Measured {
        var free: CGFloat = 0
        /// Measured since the rows came or went; until then `free` is stale.
        var fresh = false
        /// Bumped by every move of the rows, so a pending reveal can tell its
        /// spot has since changed.
        var moves = 0
    }

    private struct Room: Equatable {
        let free: CGFloat
        /// How far the list is pulled down past its top.
        let pulled: CGFloat
    }

    private var measures: Bool {
        if #available(iOS 18.0, *) { return true }
        return false
    }

    func body(content: Content) -> some View {
        let list = content
            .refreshable {
                refreshing = true
                await refresh()
                // The spinner is still folding away when the refresh returns.
                try? await Task.sleep(nanoseconds: 700_000_000)
                refreshing = false
                settle()
            }
            .onAppear { settle() }
            .onChange(of: present) {
                measured.fresh = false
                settle()
            }
            .onChange(of: ready) { settle() }
            // Should the list never report its size, show the rows where they are.
            .task(id: present && ready) {
                guard present && ready else { return }
                try? await Task.sleep(nanoseconds: 800_000_000)
                if !Task.isCancelled && !rest.shown {
                    withAnimation(.easeOut(duration: 0.15)) { rest.shown = true }
                }
            }
        if #available(iOS 18.0, *) {
            list.onScrollGeometryChange(for: Room.self) { geo in
                Room(free: geo.containerSize.height - geo.contentInsets.top - geo.contentInsets.bottom
                         - geo.contentSize.height,
                     pulled: max(0, -(geo.contentOffset.y + geo.contentInsets.top)))
            } action: { _, room in
                measured.free = room.free
                measured.fresh = true
                if !refreshing && room.pulled < 1 { settle() }
            }
        } else {
            list
        }
    }

    /// Applied after the layout pass that measured it: a list changed from
    /// inside its own layout keeps the section header at its old height, drawn
    /// over the rows below it. Nothing about the move is animated.
    private func settle() {
        guard measures else {
            if !rest.shown { rest.shown = true }
            return
        }
        guard present else {
            if rest != UsageRest() { place(gap: 0, hiding: true) }
            return
        }
        guard measured.fresh else { return }
        let gap = max(0, (rest.gap + measured.free).rounded(.down))
        if abs(gap - rest.gap) >= 1 {
            place(gap: gap)
        } else if ready && !rest.shown {
            revealIfStill(measured.moves)
        }
    }

    private func place(gap: CGFloat, hiding: Bool = false) {
        measured.moves += 1
        DispatchQueue.main.async {
            var quiet = Transaction()
            quiet.disablesAnimations = true
            withTransaction(quiet) {
                rest.gap = gap
                if hiding { rest.shown = false }
            }
        }
    }

    /// A list sizes its rows over more than one pass as a screen opens, and
    /// animates what loads into it, so the rows show only once their spot has
    /// held for as long as such an animation runs.
    private func revealIfStill(_ moves: Int) {
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
            guard measured.moves == moves, !rest.shown else { return }
            withAnimation(.easeOut(duration: 0.15)) { rest.shown = true }
        }
    }
}

private struct FeedsUsageRows: ViewModifier {
    @Environment(AppModel.self) private var model

    private struct Feed: Hashable {
        let ready: Bool
        let store: ObjectIdentifier
    }

    func body(content: Content) -> some View {
        content.task(id: Feed(ready: model.link.isReady, store: ObjectIdentifier(model.usage))) {
            guard model.link.isReady else { return }
            let usage = model.usage
            usage.rowsDidAppear()
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 180_000_000_000)
                if !Task.isCancelled { usage.refreshExtras() }
            }
            usage.rowsDidDisappear()
        }
    }
}
