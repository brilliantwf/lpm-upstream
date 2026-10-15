import Foundation

extension AppModel {
    func wireAutoPull(_ c: LpmClient) {
        c.onAutoPullMode = { [weak self] mode in self?.autoPullMode = mode }
        // A refused choice leaves the optimistic row wearing something the Mac
        // never wrote, so re-ask for the list rather than trusting the next push.
        c.onSetAutoPull = { [weak self] _, error in
            guard let self, let error else { return }
            self.actionError = error
            self.client?.requestProjects()
        }
        c.onGitAutoPullUndo = { [weak self] project, error in
            self?.git.finishAutoPullUndo(project, error: error)
        }
    }

    /// Set (or, with nil, clear so it follows) a project's own auto pull
    /// choice. The menu shows it at once; the projects-changed push replaces it
    /// with what the Mac wrote.
    func setAutoPull(_ project: String, _ value: Bool?) {
        if let idx = projects.firstIndex(where: { $0.name == project }) {
            projects[idx] = projects[idx].withAutoPull(value)
        }
        Haptics.tap()
        client?.setAutoPull(project: project, value: value)
    }

    /// The project a duplicate or worktree follows, while it's in the list.
    func parentProject(of project: Project) -> Project? {
        guard project.isDuplicate else { return nil }
        return projects.first { $0.name == project.parentName }
    }
}
