import Foundation

/// How a started service ended, in the desktop's words (serviceExit.ts).
enum ServiceExit {
    /// Ended on its own with a failure.
    static func crashed(_ code: Int?) -> Bool { code.map { $0 != 0 } ?? false }

    /// "exited with code 1", "exited", or "stopped" when the shell couldn't say.
    static func text(_ code: Int?) -> String {
        guard let code else { return "stopped" }
        return code == 0 ? "exited" : "exited with code \(code)"
    }
}

extension Service {
    var crashed: Bool { exited && ServiceExit.crashed(exitCode) }
    var exitText: String { ServiceExit.text(exitCode) }
}

extension Project {
    /// The started services that are still running.
    var liveServiceNames: Set<String> {
        running ? Set(services.filter { !$0.exited }.map(\.name)) : []
    }

    /// The started services whose command ended, by name. Their panes and logs stay
    /// until they're restarted or stopped.
    var endedServices: [String: Service] {
        guard running else { return [:] }
        return Dictionary(services.filter(\.exited).map { ($0.name, $0) }, uniquingKeysWith: { a, _ in a })
    }

    /// Something of the project still runs: once every service it started has
    /// ended, it's up only in that their tabs are still open.
    var isUp: Bool { running && (services.isEmpty || services.contains { !$0.exited }) }

    /// "web exited with code 1, worker exited with code 2", or nil when no
    /// started service crashed.
    var crashSummary: String? {
        guard running else { return nil }
        let crashed = services.filter(\.crashed)
        if crashed.isEmpty { return nil }
        return crashed.map { "\($0.name) \($0.exitText)" }.joined(separator: ", ")
    }
}
