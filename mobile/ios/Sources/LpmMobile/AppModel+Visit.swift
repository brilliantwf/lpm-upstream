import Foundation

/// A project of one of the live Mac's machines, or one of its terminals, opened
/// without leaving the live Mac.
struct MachineVisit: Hashable, Identifiable {
    let slug: String
    let project: String
    var terminal: String?

    var id: String { [slug, project, terminal ?? ""].joined(separator: "\n") }
}

/// Opening a machine's project keeps the live Mac connected: the machine gets a
/// session of its own — on its own saved entry, or through the live Mac — that
/// lasts while its screens are open and a little after, so hopping between its
/// projects doesn't reconnect each time.
extension AppModel {
    private static let visitLingerSeconds: TimeInterval = 60

    func beginVisit(_ section: MachineSection, project: String, terminal: String? = nil) -> MachineVisit? {
        guard !isVisit, let record = section.record ?? recordThroughLiveMac(section, saving: false) else {
            return nil
        }
        visitLinger?.cancel()
        visitLinger = nil
        if let session = visitSession, !session.isVisiting(record) { endVisit() }
        if visitSession == nil { visitSession = Self.visit(record, from: self) }
        return MachineVisit(slug: section.machine.slug, project: project, terminal: terminal)
    }

    /// The visit's screens were closed: end its session unless one opens again soon.
    func leaveVisit() {
        guard visitSession != nil else { return }
        visitLinger?.cancel()
        let work = DispatchWorkItem { [weak self] in self?.endVisit() }
        visitLinger = work
        DispatchQueue.main.asyncAfter(deadline: .now() + Self.visitLingerSeconds, execute: work)
    }

    func endVisit() {
        visitLinger?.cancel()
        visitLinger = nil
        guard let session = visitSession else { return }
        visitSession = nil
        session.resetSessionState()
    }

    private func isVisiting(_ record: MacRecord) -> Bool {
        guard let current = activeRecord else { return false }
        return current.localId == record.localId || (record.via != nil && current.via == record.via)
    }

    private static func visit(_ record: MacRecord, from host: AppModel) -> AppModel {
        let session = AppModel(visitHost: host)
        let saved = host.macs.contains { $0.localId == record.localId }
        session.macs = saved ? host.macs : host.macs + [record]
        session.activeMacId = record.localId
        session.setInForeground(host.inForeground)
        if let cred = session.activeCredential() { session.connectBest(credential: cred) }
        return session
    }
}
