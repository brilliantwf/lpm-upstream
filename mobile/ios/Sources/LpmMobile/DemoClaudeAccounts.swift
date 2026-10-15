import Foundation

extension DemoWorld {
    /// The simulated Mac's Claude accounts: the main login, two personal plans
    /// (one not yet confirmed for taking turns), a team seat that isn't allowed,
    /// and one signed out, with switching on.
    struct ClaudeDemo {
        struct Account {
            var id: String
            var label: String
            var signedIn: Bool
            var email: String
            var plan: String
        }

        var accounts: [Account] = [
            Account(id: claudeMainLogin, label: "", signedIn: true, email: "you@example.com", plan: "max"),
            Account(id: "work", label: "Work", signedIn: true, email: "you@work.example", plan: "max"),
            Account(id: "personal", label: "Personal", signedIn: true, email: "me@example.com", plan: "pro"),
            Account(id: "client", label: "Client", signedIn: true, email: "dev@client.example", plan: "team"),
            Account(id: "laptop", label: "Old laptop", signedIn: false, email: "", plan: ""),
        ]
        var confirmed: Set<String> = [claudeMainLogin, "work"]
    }
}

/// Demo handlers for the Claude Account menu: the accounts and switching
/// state, and setting a project's choice as the Mac would write it.
extension DemoServer {
    func registerClaudeAccountHandlers() {
        register("claudeAccounts") { [weak self] _ in
            self?.pushAfter(0.2) { [weak self] in self?.claudeAccountsPayload() }
        }
        register("setClaudeAccount") { [weak self] o in
            guard let self, let project = o["project"] as? String else { return }
            guard let choice = (o["choice"] as? [String: Any]).flatMap(ClaudeAccountChoice.init(wire:)),
                  let idx = self.world.projectIndex(project) else {
                self.push(["t": "setClaudeAccount", "project": project, "ok": false,
                           "error": "That project isn't on this Mac."])
                return
            }
            self.world.claude.confirmed.formUnion(o["confirm"] as? [String] ?? [])
            let own = choice.own(isCopy: !self.world.projects[idx].parentName.isEmpty)
            self.world.projects[idx].claudeAccount = own.account
            self.world.projects[idx].claudeAccounts = own.accounts
            self.push(["t": "setClaudeAccount", "project": project, "ok": true])
            self.pushAfter(0.1) { ["t": "projects-changed"] }
        }
    }

    /// Each list takes turns starting on its first signed-in account; the main
    /// accounts take turns between the main login and Work, on the main login now.
    private func claudeAccountsPayload() -> [String: Any] {
        let claude = world.claude
        let signedIn = Set(claude.accounts.filter(\.signedIn).map(\.id))
        var pools: [[String: Any]] = [
            ["project": NSNull(), "members": [claudeMainLogin, "work"], "current": claudeMainLogin],
        ]
        for p in world.projects where p.claudeAccount == nil {
            if let list = p.claudeAccounts, let first = list.first(where: signedIn.contains) {
                pools.append(["project": p.name, "members": list, "current": first])
            }
        }
        return [
            "t": "claudeAccounts",
            "ok": true,
            "accounts": claude.accounts.map { a -> [String: Any] in
                ["id": a.id, "label": a.label, "signedIn": a.signedIn, "email": a.email,
                 "plan": a.plan, "confirmed": claude.confirmed.contains(a.id)]
            },
            "switching": [
                "enabled": true,
                "active": true,
                "paused": NSNull(),
                "allowed": [String](),
                "maxMembers": 3,
                "pools": pools,
            ],
        ]
    }
}
