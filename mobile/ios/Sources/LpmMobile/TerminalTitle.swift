import SwiftUI

/// A terminal screen's title: the tab's name over how long its agent has been at
/// it — "3m 5s". The connection's status takes that line whenever the Mac isn't
/// connected; a plain shell has no line at all.
struct TerminalTitle: View {
    @Environment(AppModel.self) private var model
    let term: TerminalInfo

    private var agent: ProjectAgentRow? {
        guard let project = model.projects.first(where: { $0.name == term.project }) else { return nil }
        let rows = projectAgentRows(project, now: Int(Date().timeIntervalSince1970 * 1000), tabTitles: [:])
        guard let agent = agentRowsByTerminal(rows)[term.id], agent.state != .idle else { return nil }
        return agent
    }

    var body: some View {
        VStack(spacing: 1) {
            Text(term.label)
                .font(.headline)
                .lineLimit(1)
            if !model.demoMode && !model.link.isReady {
                LinkStatusLabel(line: model.link.statusLine)
            } else if let agent, let since = agent.since {
                ElapsedText(since: since, until: agent.until, precise: true)
                    .font(.caption2.weight(.medium))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
        .accessibilityElement(children: .combine)
    }
}
