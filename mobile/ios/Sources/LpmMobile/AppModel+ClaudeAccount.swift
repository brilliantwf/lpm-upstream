import Foundation

extension AppModel {
    func wireClaudeAccounts(_ c: LpmClient) {
        c.onClaudeAccounts = { [weak self] accounts in
            if let accounts { self?.claudeAccounts = accounts }
        }
        // A refused choice leaves the optimistic menu showing something the Mac
        // never wrote, so re-ask for the list. Either way the pools may have
        // moved, so the accounts are read again too.
        c.onSetClaudeAccount = { [weak self] _, error in
            guard let self else { return }
            if let error {
                self.actionError = error
                self.client?.requestProjects()
            }
            self.client?.requestClaudeAccounts()
        }
    }

    /// Read the accounts the Claude Account menu lists, and the usage readings
    /// its rows show when none have arrived yet.
    func loadClaudeAccounts() {
        client?.requestClaudeAccounts()
        if usage.snapshot == nil { client?.requestLimits() }
    }

    /// Set which login new Claude sessions in a project use, first confirming
    /// `confirm` as the person's own. The menu shows it at once; the
    /// projects-changed push replaces it with what the Mac wrote.
    func setClaudeAccount(_ project: Project, _ choice: ClaudeAccountChoice, confirm: [String] = []) {
        if let idx = projects.firstIndex(where: { $0.name == project.name }) {
            projects[idx] = projects[idx].withClaude(choice.own(isCopy: project.isDuplicate))
        }
        Haptics.tap()
        client?.setClaudeAccount(project: project.name, choice: choice, confirm: confirm)
    }
}
