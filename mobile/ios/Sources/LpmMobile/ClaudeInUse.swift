import Foundation

/// Which Claude account new sessions run on and the rest of the pool they can
/// switch to — the desktop's `useProjectClaudeAccounts`, which tags the usage
/// rows "in use" and "pool". On the projects list nothing is selected, so the
/// rows speak for the main accounts; a project's screen speaks for that project.
struct ClaudeInUse {
    /// The account's limits key; "default" is the main login.
    let current: String
    let standby: [String]
    /// The project the tags speak for, nil for the main accounts.
    let project: Project?
    /// Pinned to one account, by the project's own keys or its parent's.
    let pinned: Bool

    func note(for account: String) -> String? {
        let place = project.map { "in \($0.label)" } ?? "on the main accounts"
        if account == current { return "New Claude sessions \(place) use this account" }
        if standby.contains(account) {
            return project.map { "\($0.label) can switch to this account" }
                ?? "The main accounts can switch to this account"
        }
        return nil
    }
}

private enum ClaudeSource {
    case pin(String)
    case list(owner: String)
    case main
}

extension ClaudeAccounts {
    /// Mirrors the desktop's `projectAccount` / `projectPool`: a copy with no keys
    /// of its own follows its parent's, and a list with no known accounts falls
    /// back to the main accounts. Nil for an SSH project, whose agents run under
    /// the far host's login.
    func inUse(_ project: Project?, parent: Project?) -> ClaudeInUse? {
        if project?.isRemote == true { return nil }
        let source = project.flatMap(Self.ownSource) ?? parent.flatMap(Self.ownSource) ?? .main
        switch source {
        case .pin(let id):
            let resolved = id.isEmpty || account(id) == nil ? claudeMainLogin : id
            return ClaudeInUse(current: resolved, standby: [], project: project, pinned: true)
        case .list(let owner):
            let key = switching.current[owner] != nil || switching.members[owner] != nil ? owner : ""
            return pool(key, project: project)
        case .main:
            return pool("", project: project)
        }
    }

    private func pool(_ key: String, project: Project?) -> ClaudeInUse {
        let current = switching.current[key] ?? claudeMainLogin
        let standby = switching.active ? (switching.members[key] ?? []).filter { $0 != current } : []
        return ClaudeInUse(current: current, standby: standby, project: project, pinned: false)
    }

    private static func ownSource(_ project: Project) -> ClaudeSource? {
        if let id = project.claude.account { return .pin(id) }
        guard let list = project.claude.accounts else { return nil }
        return list.isEmpty ? .main : .list(owner: project.name)
    }
}
