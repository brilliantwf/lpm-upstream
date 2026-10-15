import Foundation

/// lpm on the live Mac: the release it runs, a newer one when there is one, and
/// an install of it there — started from this iPhone, another phone, or the Mac
/// itself. The Mac owns the install; this mirrors what it reports.
@Observable @MainActor
final class MacUpdateStore {
    enum State: Equatable {
        case checking
        /// Whole percent; -1 until the size is known.
        case downloading(Int)
        /// Replacing the app, past the point it can be cancelled.
        case installing
        /// The connection dropped mid-install: lpm is restarting there.
        case restarting
        /// Back on the new release.
        case updated(String)

        var inProgress: Bool {
            if case .updated = self { return false }
            return true
        }

        var cancellable: Bool {
            switch self {
            case .checking, .downloading: return true
            default: return false
            }
        }
    }

    /// A check asked for from the menu, until it turns up a release to offer.
    enum Lookup: Equatable {
        case checking
        case upToDate
        case failed(String)
    }

    @ObservationIgnored weak var model: AppModel?

    private(set) var version: String?
    private(set) var canUpdate = false
    /// The release the Mac could move to; nil while it is up to date.
    private(set) var newer: String?
    private(set) var state: State?
    private(set) var cancelling = false
    /// The bar was closed for this release, so it stays away until the next one.
    private(set) var dismissed: String?
    private(set) var lookup: Lookup?
    var error: String?

    /// What the Mac ran when the install began. Coming back on anything else is
    /// the update having landed.
    @ObservationIgnored private var from: String?
    @ObservationIgnored private var giveUp: DispatchWorkItem?
    @ObservationIgnored private var fade: DispatchWorkItem?
    @ObservationIgnored private var lookupFade: DispatchWorkItem?

    /// How long a Mac restarting lpm has to come back before the bar stops
    /// saying so; the connection status explains the rest.
    private static let restartGrace: TimeInterval = 180
    private static let updatedFor: TimeInterval = 8
    private static let upToDateFor: TimeInterval = 5

    var canInstall: Bool {
        guard canUpdate, newer != nil else { return false }
        return state?.inProgress != true
    }

    var canCheck: Bool {
        canUpdate && lookup != .checking && state?.inProgress != true
    }

    var showsBar: Bool {
        if state != nil || error != nil || lookup != nil { return true }
        guard canUpdate, let newer else { return false }
        return newer != dismissed
    }

    var busyPlaces: [BusyAgentPlace] { busyAgentPlaces(model?.projects ?? []) }

    // MARK: from the Mac

    func noteBuild(_ build: MacBuild?) {
        if let current = build?.version, let from, current != from, state?.inProgress == true {
            land(on: current)
        }
        version = build?.version
        canUpdate = build?.selfUpdate ?? false
        dismissed = UserDefaults.standard.string(forKey: dismissKey)
        if canUpdate {
            model?.client?.updateCheck()
        } else {
            newer = nil
        }
    }

    func apply(_ check: MacUpdateCheck) {
        if let current = check.currentVersion { version = current }
        newer = check.updateAvailable ? check.latestVersion : nil
        if lookup == .checking { answer(check) }
        apply(check.step, caughtUp: true)
    }

    /// `caughtUp` is the Mac answering a reconnect: idle then means an install
    /// this phone was following ended while it was away.
    func apply(_ step: MacUpdateStep, caughtUp: Bool = false) {
        switch step.phase {
        case .checking: follow(.checking)
        case .downloading: follow(.downloading(step.progress))
        case .installing:
            cancelling = false
            follow(.installing)
        case .idle:
            guard let state, state.inProgress else { return }
            if caughtUp && (state == .installing || state == .restarting) {
                error = "The update didn't finish on \(model?.link.macLabel ?? "your Mac"). Try again."
            }
            settle()
        }
    }

    func finished(cancelled: Bool, error: String?) {
        if let error { self.error = error }
        if state?.inProgress == true { settle() }
    }

    func cancelAnswered(stopping: Bool) {
        if !stopping { cancelling = false }
    }

    func handleConnectionReset() {
        cancelling = false
        clearLookup()
        guard state == .installing else { return }
        state = .restarting
        giveUp?.cancel()
        let work = DispatchWorkItem { [weak self] in
            guard let self, self.state == .restarting else { return }
            self.settle()
        }
        giveUp = work
        DispatchQueue.main.asyncAfter(deadline: .now() + Self.restartGrace, execute: work)
    }

    // MARK: from this iPhone

    /// Asks the Mac for the newest release now, past the hour it keeps an answer.
    func checkNow() {
        guard canCheck, let client = model?.client else { return }
        lookupFade?.cancel()
        error = nil
        lookup = .checking
        client.updateCheck(force: true)
    }

    func install() {
        guard canInstall, let client = model?.client else { return }
        clearLookup()
        error = nil
        from = version
        cancelling = false
        state = .checking
        client.updateInstall()
    }

    func cancel() {
        guard state?.cancellable == true, !cancelling else { return }
        cancelling = true
        model?.client?.updateCancel()
    }

    func dismiss() {
        if lookup != nil {
            clearLookup()
            return
        }
        if error != nil {
            error = nil
            return
        }
        dismissed = newer
        UserDefaults.standard.set(newer, forKey: dismissKey)
    }

    // MARK: -

    private var dismissKey: String {
        let id = model?.activeRecord?.serverId ?? model?.activeMacId?.uuidString ?? ""
        return "macUpdate.dismissed.\(id)"
    }

    /// A release turned up brings the bar back even if it was closed for it.
    private func answer(_ check: MacUpdateCheck) {
        if let error = check.error {
            lookup = .failed(error)
        } else if newer != nil {
            lookup = nil
            dismissed = nil
            UserDefaults.standard.removeObject(forKey: dismissKey)
        } else {
            lookup = .upToDate
            let work = DispatchWorkItem { [weak self] in
                guard let self, self.lookup == .upToDate else { return }
                self.lookup = nil
            }
            lookupFade = work
            DispatchQueue.main.asyncAfter(deadline: .now() + Self.upToDateFor, execute: work)
        }
    }

    private func clearLookup() {
        lookupFade?.cancel()
        lookup = nil
    }

    private func follow(_ next: State) {
        clearLookup()
        if from == nil { from = version }
        giveUp?.cancel()
        fade?.cancel()
        state = next
    }

    private func settle() {
        giveUp?.cancel()
        state = nil
        cancelling = false
        from = nil
    }

    private func land(on release: String) {
        settle()
        newer = nil
        error = nil
        state = .updated(release)
        Haptics.success()
        let work = DispatchWorkItem { [weak self] in
            guard let self, case .updated = self.state else { return }
            self.state = nil
        }
        fade = work
        DispatchQueue.main.asyncAfter(deadline: .now() + Self.updatedFor, execute: work)
    }
}
