import Foundation

/// One reply in the chunked attach: `uploadBegin` carries the upload's id and
/// piece size, `uploadChunk` how much has landed, `uploadDone` the path to paste.
struct UploadStep {
    let termId: String
    let reqId: String
    let ok: Bool
    let error: String?
    let uploadId: String
    let chunk: Int64
    let received: Int64
    let path: String

    init(_ o: [String: Any]) {
        termId = o["id"] as? String ?? ""
        reqId = Wire.reqIdString(o["reqId"])
        ok = o["ok"] as? Bool ?? false
        error = o["error"] as? String
        uploadId = o["uploadId"] as? String ?? ""
        chunk = (o["chunk"] as? NSNumber)?.int64Value ?? 0
        received = (o["received"] as? NSNumber)?.int64Value ?? 0
        path = o["path"] as? String ?? ""
    }
}

/// Sends one file to the Mac a terminal runs on, a piece at a time. Each piece
/// waits for the last one's acknowledgement because the Mac appends them in the
/// order they land, and only one piece is ever in memory, so a long video is
/// bounded by the Mac's disk rather than by one WebSocket frame.
@MainActor
final class ChunkedUpload {
    enum Outcome {
        case done(path: String)
        /// `refusal` is the Mac's own reason, worth showing as is; without one the
        /// link failed and a retry may well work.
        case failed(refusal: String?)
    }

    private enum Phase { case beginning, sending, finishing, over }

    static let feature = "fileUpload"
    private static let stepTimeout: TimeInterval = 60
    // The Mac may still have to scp the file on to an ssh-backed pane.
    private static let doneTimeout: TimeInterval = 600

    let file: URL
    /// The step in flight; a reply for any other is stale.
    private(set) var reqId = ""
    private let termId: String
    private weak var model: AppModel?
    private let onProgress: (Double) -> Void
    private let onFinish: (Outcome) -> Void
    private var phase = Phase.beginning
    private var uploadId = ""
    private var chunkSize: Int64 = 1 << 20
    private var size: Int64 = 0
    private var offset: Int64 = 0
    private var watchdog: Task<Void, Never>?

    init(file: URL, termId: String, model: AppModel?,
         onProgress: @escaping (Double) -> Void, onFinish: @escaping (Outcome) -> Void) {
        self.file = file
        self.termId = termId
        self.model = model
        self.onProgress = onProgress
        self.onFinish = onFinish
    }

    func start() {
        guard model?.connection == .ready, let client = model?.client else {
            return finish(.failed(refusal: nil))
        }
        guard let bytes = try? file.resourceValues(forKeys: [.fileSizeKey]).fileSize else {
            return finish(.failed(refusal: "Couldn't read \(file.lastPathComponent)."))
        }
        size = Int64(bytes)
        client.uploadBegin(termId, name: file.lastPathComponent, bytes: size, reqId: nextReqId())
        armTimeout(Self.stepTimeout)
    }

    func apply(_ step: UploadStep) {
        guard phase != .over, step.reqId == reqId else { return }
        watchdog?.cancel()
        guard step.ok else { return finish(.failed(refusal: step.error ?? "Your Mac refused the file.")) }
        switch phase {
        case .beginning:
            uploadId = step.uploadId
            if step.chunk > 0 { chunkSize = step.chunk }
            phase = .sending
            sendNext()
        case .sending:
            offset = step.received
            onProgress(Double(offset) / Double(max(size, 1)))
            sendNext()
        case .finishing:
            finish(.done(path: step.path))
        case .over:
            break
        }
    }

    /// Stop without reporting — the chip is gone or the link dropped — and let
    /// the Mac free whatever it has written so far.
    func cancel() {
        guard phase != .over else { return }
        phase = .over
        watchdog?.cancel()
        abortOnMac()
    }

    private func sendNext() {
        guard offset < size else {
            phase = .finishing
            model?.client?.uploadDone(termId, uploadId: uploadId, reqId: nextReqId())
            armTimeout(Self.doneTimeout)
            return
        }
        let (file, offset, length) = (file, offset, min(chunkSize, size - offset))
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            let b64 = Self.readPiece(file, offset: offset, length: length)?.base64EncodedString()
            DispatchQueue.main.async {
                guard let self, self.phase == .sending, self.offset == offset else { return }
                guard let b64 else {
                    return self.finish(.failed(refusal: "Couldn't read \(file.lastPathComponent)."))
                }
                self.model?.client?.uploadChunk(self.termId, uploadId: self.uploadId, offset: offset,
                                                b64: b64, reqId: self.nextReqId())
                self.armTimeout(Self.stepTimeout)
            }
        }
    }

    private nonisolated static func readPiece(_ file: URL, offset: Int64, length: Int64) -> Data? {
        guard let handle = try? FileHandle(forReadingFrom: file) else { return nil }
        defer { try? handle.close() }
        guard (try? handle.seek(toOffset: UInt64(offset))) != nil,
              let data = try? handle.read(upToCount: Int(length)), data.count == Int(length)
        else { return nil }
        return data
    }

    private func finish(_ outcome: Outcome) {
        guard phase != .over else { return }
        if case .failed = outcome { abortOnMac() }
        phase = .over
        watchdog?.cancel()
        onFinish(outcome)
    }

    private func abortOnMac() {
        guard !uploadId.isEmpty else { return }
        model?.client?.uploadAbort(termId, uploadId: uploadId)
    }

    private func nextReqId() -> String {
        reqId = UUID().uuidString
        return reqId
    }

    /// A reply can be lost to a full queue on the Mac; without this the chip would
    /// spin forever.
    private func armTimeout(_ seconds: TimeInterval) {
        watchdog?.cancel()
        watchdog = Task { [weak self] in
            try? await Task.sleep(for: .seconds(seconds))
            guard !Task.isCancelled else { return }
            self?.finish(.failed(refusal: nil))
        }
    }
}
