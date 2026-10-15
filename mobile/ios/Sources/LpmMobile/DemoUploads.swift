import Foundation

/// Demo handlers for the chunked attach (videos and files from the composer).
/// Nothing is kept but the name, and each piece is acknowledged as if written.
extension DemoServer {
    func registerUploadHandlers() {
        register("uploadBegin") { [weak self] o in
            guard let self, let id = o["id"] as? String else { return }
            let uploadId = UUID().uuidString
            self.world.uploadNames[uploadId] = o["name"] as? String ?? "file"
            self.pushAfter(0.2, ["t": "uploadBegin", "id": id, "ok": true, "uploadId": uploadId,
                                 "chunk": 1 << 20, "reqId": o["reqId"] ?? NSNull()])
        }
        register("uploadChunk") { [weak self] o in
            guard let self, let id = o["id"] as? String else { return }
            let offset = (o["offset"] as? NSNumber)?.int64Value ?? 0
            let length = Int64(Data(base64Encoded: o["b64"] as? String ?? "")?.count ?? 0)
            self.pushAfter(0.05, ["t": "uploadChunk", "id": id, "ok": true,
                                  "received": offset + length, "reqId": o["reqId"] ?? NSNull()])
        }
        register("uploadDone") { [weak self] o in
            guard let self, let id = o["id"] as? String else { return }
            let name = self.world.uploadNames.removeValue(forKey: o["uploadId"] as? String ?? "") ?? "file"
            self.pushAfter(0.2, ["t": "uploadDone", "id": id, "ok": true,
                                 "path": "/Users/demo/uploads/\(name)", "reqId": o["reqId"] ?? NSNull()])
        }
        register("uploadAbort") { [weak self] o in
            self?.world.uploadNames[o["uploadId"] as? String ?? ""] = nil
        }
    }
}
