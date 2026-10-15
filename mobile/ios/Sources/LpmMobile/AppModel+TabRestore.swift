import Foundation

/// lpm reopens a Mac's tabs under new ids when it starts again. A terminal open
/// on the phone across that restart ends, its process gone with the old lpm,
/// while the same tab is back in the project's list. These tell that apart from
/// a real exit and find the tab that took the old one's place.
extension AppModel {
    /// How long before the link dropped an ending still counts as lpm quitting.
    private static let quitWindow: TimeInterval = 10

    func recordEnding(_ id: String, code: Int, gone: Bool) {
        if var earlier = endedTerminals[id] {
            earlier.gone = earlier.gone || gone
            endedTerminals[id] = earlier
            return
        }
        let listed = terminals.values.lazy.compactMap { list in
            list.firstIndex { $0.id == id }.map { (list[$0].label, $0) }
        }.first
        endedTerminals[id] = TerminalEnding(code: code, gone: gone, label: listed?.0, index: listed?.1)
    }

    /// The link dropped: terminals that ended just before it went down with lpm.
    func noteTerminalsLostWithLink() {
        let now = Date()
        for (id, ending) in endedTerminals
        where !ending.restarted && now.timeIntervalSince(ending.at) < Self.quitWindow {
            endedTerminals[id]?.restarted = true
        }
    }

    /// The tab lpm reopened in place of `term`, once the project's list shows
    /// it: same name, a new id, and when several share the name, the one in the
    /// old tab's place.
    func restoredTab(for term: TerminalInfo) -> TerminalInfo? {
        guard let ending = endedTerminals[term.id], ending.replaceable,
              let list = terminals[term.project], !list.contains(where: { $0.id == term.id }) else { return nil }
        let label = ending.label ?? term.label
        let named = list.filter { $0.label == label }
        if named.count > 1, let index = ending.index, list.indices.contains(index), list[index].label == label {
            return list[index]
        }
        return named.first
    }

    /// Ask for a project's tabs again once the link is back. An empty list from
    /// before the drop may only mean lpm was restarting, so until the Mac
    /// answers it reads as loading rather than as a project with no tabs.
    func reloadTerminalsAfterReconnect(_ project: String) {
        if terminals[project]?.isEmpty == true { terminals[project] = nil }
        loadTerminals(project)
    }
}
