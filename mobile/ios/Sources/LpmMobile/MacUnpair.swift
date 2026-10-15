import Foundation

/// Tells a Mac the user removed it from this phone, so it drops this phone's
/// device record instead of trusting — and pushing to — a credential the phone no
/// longer has. One short-lived connection per address, on the pinned certificate
/// only. Best effort: removal never waits for it, and a Mac that is off keeps the
/// record until it's revoked there.
enum MacUnpair {
    private static let timeout: TimeInterval = 8

    static func send(hosts: [String], port: Int, credential: LpmClient.Credential, pin: String?) {
        // Without a pin, whoever answers at the address could be anyone; the token
        // isn't handed to them.
        guard let pin else { return }
        let frame = Wire.unpair(deviceId: credential.deviceId, token: credential.token)
        Task.detached {
            for host in hosts where !host.isEmpty {
                if await deliver(frame, host: host, port: port, pin: pin) { return }
            }
        }
    }

    /// Whether the Mac at `host` answered at all, so the next address isn't tried.
    private static func deliver(_ frame: String, host: String, port: Int, pin: String) async -> Bool {
        guard let url = LpmClient.Endpoint(host: host, port: port).dialURL() else { return false }
        let config = URLSessionConfiguration.ephemeral
        config.waitsForConnectivity = false
        config.timeoutIntervalForRequest = timeout
        let session = URLSession(configuration: config,
                                 delegate: PinningDelegate(pinProvider: { pin }, expected: nil),
                                 delegateQueue: nil)
        defer { session.invalidateAndCancel() }
        let task = session.webSocketTask(with: url)
        task.resume()
        DispatchQueue.global().asyncAfter(deadline: .now() + timeout) {
            task.cancel(with: .goingAway, reason: nil)
        }
        do {
            try await task.send(.string(frame))
            _ = try await task.receive()
            return true
        } catch {
            return false
        }
    }
}
