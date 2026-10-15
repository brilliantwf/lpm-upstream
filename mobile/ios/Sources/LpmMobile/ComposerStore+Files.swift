import Foundation

/// Files and videos: copied to scratch first, then sent in pieces to a Mac that
/// takes them, or in one frame to an older one, up to what a frame can hold.
extension ComposerStore {
    /// What an older Mac takes in one frame: it refuses frames over 16 MiB, and
    /// base64 inflates by 4/3.
    static let singleFrameLimit = 8 * 1024 * 1024

    func addFile(_ url: URL) {
        let att = Attachment(kind: .file, filename: url.lastPathComponent,
                             mime: UploadScratch.mime(url), thumbnail: nil)
        if tabs.indices.contains(activeIndex) { tabs[activeIndex].attachments.append(att) }
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            let scoped = url.startAccessingSecurityScopedResource()
            let copy = try? UploadScratch.copy(url)
            if scoped { url.stopAccessingSecurityScopedResource() }
            DispatchQueue.main.async {
                guard let self else { return }
                guard let copy else { return self.removeAttachment(att.id) }
                self.adopt(att.id, file: copy)
            }
        }
    }

    /// Hold a video's place in selection order while the library hands it over,
    /// which can take a while for one that is only in iCloud.
    func reserveVideoPlaceholder() -> UUID {
        let att = Attachment(kind: .video, filename: "Video", mime: "video/quicktime", thumbnail: nil)
        if tabs.indices.contains(activeIndex) { tabs[activeIndex].attachments.append(att) }
        return att.id
    }

    func fillVideoUpload(_ id: UUID, file: URL?) {
        guard let file else {
            removeAttachment(id)
            uploadError = "Couldn't load that video from your library."
            return
        }
        updateAttachment(id) { att in
            att.filename = file.lastPathComponent
            att.mime = UploadScratch.mime(file)
        }
        // Before the upload: a finished one deletes the copy this reads.
        Task { [weak self] in
            let thumbnail = await UploadScratch.videoThumbnail(file)
            guard let self else { return UploadScratch.discard(file) }
            self.updateAttachment(id) { $0.thumbnail = thumbnail }
            self.adopt(id, file: file)
        }
    }

    /// Send a scratch copy by whichever path this Mac speaks.
    func uploadFile(_ id: UUID, file: URL) {
        updateAttachment(id) { att in
            att.status = .uploading
            att.progress = nil
        }
        if model?.client?.hostFeatures.contains(ChunkedUpload.feature) == true {
            sendInPieces(id, file: file)
        } else {
            sendInOneFrame(id, file: file)
        }
    }

    func applyUploadStep(_ step: UploadStep) {
        fileUploads.values.first { $0.reqId == step.reqId }?.apply(step)
    }

    /// Drop the copies, and stop the uploads, of attachments no tab holds anymore.
    func pruneFileSources() {
        let live = Set(tabs.flatMap(\.attachments).map(\.id))
        for (id, file) in fileSources where !live.contains(id) {
            fileUploads.removeValue(forKey: id)?.cancel()
            fileSources[id] = nil
            UploadScratch.discard(file)
        }
    }

    /// Take ownership of a fresh scratch copy, unless its chip was removed while
    /// the copy was being made.
    private func adopt(_ id: UUID, file: URL) {
        guard tabs.contains(where: { $0.attachments.contains { $0.id == id } }) else {
            return UploadScratch.discard(file)
        }
        fileSources[id] = file
        uploadFile(id, file: file)
    }

    private func sendInPieces(_ id: UUID, file: URL) {
        let upload = ChunkedUpload(
            file: file, termId: termId, model: model,
            onProgress: { [weak self] fraction in self?.updateAttachment(id) { $0.progress = fraction } },
            onFinish: { [weak self] outcome in self?.finishInPieces(id, outcome) })
        fileUploads[id] = upload
        upload.start()
    }

    private func finishInPieces(_ id: UUID, _ outcome: ChunkedUpload.Outcome) {
        fileUploads[id] = nil
        switch outcome {
        case .done(let path):
            updateAttachment(id) { $0.status = .uploaded(path) }
            if let file = fileSources.removeValue(forKey: id) { UploadScratch.discard(file) }
        case .failed(let refusal):
            updateAttachment(id) { $0.status = .failed("Upload failed") }
            if let refusal { uploadError = refusal.prefix(1).uppercased() + refusal.dropFirst() }
        }
    }

    private func sendInOneFrame(_ id: UUID, file: URL) {
        let size = (try? file.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0
        guard size <= Self.singleFrameLimit else {
            removeAttachment(id)
            uploadError = "\(file.lastPathComponent) is \(size / (1024 * 1024)) MB. "
                + "Update lpm on your Mac to attach files over 8 MB from your \(ThisDevice.noun)."
            return
        }
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            let b64 = (try? Data(contentsOf: file))?.base64EncodedString()
            DispatchQueue.main.async {
                guard let self, self.fileSources[id] == file else { return }
                guard let b64 else { return self.removeAttachment(id) }
                // The one-frame path keeps its own payload for a retry.
                self.fileSources[id] = nil
                UploadScratch.discard(file)
                self.dispatchUpload(id, b64: b64, mime: UploadScratch.mime(file),
                                    name: file.lastPathComponent)
            }
        }
    }
}
