import Foundation

/// One agent of a project, as the projects list shows it under the project's row.
/// Mirrors the desktop sidebar's sidebarAgents.ts.
struct ProjectAgentRow: Identifiable, Equatable {
    /// The status key, unique within its project.
    var id: String
    var state: AgentState
    /// What the agent's terminal tab is called. Falls back to the agent's own name
    /// until that project's tabs have loaded, which is where the names come from.
    var title: String
    var provider: String
    var terminalId: String?
    /// Millis: what the row's reading counts from — the turn's start for work
    /// running or finished, the state's own start for a wait. Nil when the turn was
    /// never seen starting, so how long it ran is unknowable.
    var since: Int?
    /// Millis: set once the turn ended, freezing the reading at how long the work
    /// took instead of counting on past it.
    var until: Int?
    /// How many further agents report on this row's tab, folded into it.
    var shared = 0

    func elapsed(now: Int) -> String? {
        since.map { shortDuration(millis: (until ?? now) - $0) }
    }
}

// A paired Mac stamps its statuses with its own clock, which can run ahead of
// ours; an unstamped entry would otherwise start in 1970.
private func stamp(_ value: Int, now: Int) -> Int? {
    value > 0 ? min(value, now) : nil
}

/// Most urgent first, and within a state the one that entered it most recently.
private func precedes(_ a: StatusEntry, _ b: StatusEntry) -> Bool {
    let rankA = AgentState.of(a.value).rank
    let rankB = AgentState.of(b.value).rank
    return rankA == rankB ? a.timestamp > b.timestamp : rankA < rankB
}

/// Every agent a project has going, in the order the list shows them: one row per
/// tab, as the desktop sidebar has it. The most urgent agent on a tab speaks for
/// it and the others are counted on it; an agent reporting against no tab keeps
/// its own row. `tabTitles` is that project's terminal id -> the name that tab is
/// showing.
func projectAgentRows(_ project: Project, now: Int, tabTitles: [String: String]) -> [ProjectAgentRow] {
    var rows: [ProjectAgentRow] = []
    var rowForTerminal: [String: Int] = [:]
    for row in agentRows(project, now: now, tabTitles: tabTitles) {
        guard let terminal = row.terminalId else {
            rows.append(row)
            continue
        }
        if let index = rowForTerminal[terminal] {
            rows[index].shared += 1
        } else {
            rowForTerminal[terminal] = rows.count
            rows.append(row)
        }
    }
    return rows
}

private func agentRows(_ project: Project, now: Int, tabTitles: [String: String]) -> [ProjectAgentRow] {
    project.statusEntries.sorted(by: precedes).map { entry in
        let provider = agentProviderLabel(forStatusKey: entry.key)
        let state = AgentState.of(entry.value)
        let stateSince = stamp(entry.timestamp, now: now) ?? now
        let turnStart = stamp(entry.turnStart, now: now)
        let tab = tabTitles[entry.paneID]
        // A finished turn reads as how long the work took, held still. Waiting and
        // a problem read as how long they have been true — that is the wait you
        // care about. Working spans a turn's approval pauses, so it counts from
        // when the agent picked the work up. Idle is nothing an agent reported, so
        // there is no turn to time.
        var since: Int?
        var until: Int?
        switch state {
        case .done:
            since = turnStart
            until = stamp(entry.endedAt, now: now) ?? stateSince
        case .working:
            since = turnStart ?? stateSince
        case .needsYou, .error:
            since = stateSince
        case .idle:
            break
        }
        return ProjectAgentRow(
            id: entry.key,
            state: state,
            title: (tab?.isEmpty == false) ? tab! : provider,
            provider: provider,
            terminalId: entry.paneID.isEmpty ? nil : entry.paneID,
            since: since,
            until: until
        )
    }
}

/// The agent each terminal reads as; `projectAgentRows` already holds one per tab.
func agentRowsByTerminal(_ rows: [ProjectAgentRow]) -> [String: ProjectAgentRow] {
    Dictionary(rows.compactMap { row in row.terminalId.map { ($0, row) } },
               uniquingKeysWith: { first, _ in first })
}

/// "45s", "4m", "2h 5m" — `formatActivityDuration` with the remainder dropped, for
/// a row with room for a reading rather than a stopwatch. Takes millis, and rounds
/// down like a stopwatch: it says what has elapsed, never more.
func shortDuration(millis: Int) -> String {
    let secs = max(0, millis / 1000)
    if secs < 60 { return "\(secs)s" }
    let mins = secs / 60
    if mins < 60 { return "\(mins)m" }
    let rest = mins % 60
    return rest > 0 ? "\(mins / 60)h \(rest)m" : "\(mins / 60)h"
}
