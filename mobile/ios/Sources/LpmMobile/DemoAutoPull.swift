import Foundation

/// Demo handlers for auto pull: a project's own choice, and undoing the last
/// auto pull (the commits it brought in show up as behind again, as after the
/// Mac's `git reset --keep`).
extension DemoServer {
    func registerAutoPullHandlers() {
        register("setAutoPull") { [weak self] o in
            guard let self, let project = o["project"] as? String else { return }
            if let idx = self.world.projectIndex(project) {
                self.world.projects[idx].autoPull = o["value"] as? Bool
            }
            self.push(["t": "setAutoPull", "project": project, "ok": true])
            self.pushAfter(0.1) { ["t": "projects-changed"] }
        }
        register("gitAutoPullUndo") { [weak self] o in
            guard let self, let project = o["project"] as? String else { return }
            self.pushAfter(0.8) { [weak self] in
                guard let self else { return nil }
                guard let pulled = self.world.git[project]?.autoPulled else {
                    return ["t": "gitAutoPullUndo", "project": project, "ok": false,
                            "error": "There's no auto pull to undo."]
                }
                self.world.git[project]?.autoPulled = nil
                self.world.git[project]?.behind += pulled["count"] as? Int ?? 0
                self.pushGitChanged(project)
                return ["t": "gitAutoPullUndo", "project": project, "ok": true]
            }
        }
    }
}
