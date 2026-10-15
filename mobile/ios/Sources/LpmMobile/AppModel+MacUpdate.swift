import Foundation

extension AppModel {
    func wireMacUpdate(_ c: LpmClient) {
        c.onBuild = { [weak self, weak c] build in
            guard let self, let c, c === self.client, !self.demoMode else { return }
            self.macUpdate.noteBuild(build)
        }
        c.onUpdateCheck = { [weak self, weak c] check in
            guard let self, let c, c === self.client else { return }
            self.macUpdate.apply(check)
        }
        c.onUpdateProgress = { [weak self, weak c] step in
            guard let self, let c, c === self.client else { return }
            self.macUpdate.apply(step)
        }
        c.onUpdateInstall = { [weak self, weak c] cancelled, error in
            guard let self, let c, c === self.client else { return }
            self.macUpdate.finished(cancelled: cancelled, error: error)
        }
        c.onUpdateCancel = { [weak self, weak c] stopping in
            guard let self, let c, c === self.client else { return }
            self.macUpdate.cancelAnswered(stopping: stopping)
        }
    }
}
