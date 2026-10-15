import AVFoundation
import CoreTransferable
import UIKit
import UniformTypeIdentifiers

/// Local copies of files waiting to go to the Mac. A picked file is only readable
/// while the picker allows it, and a chunked upload reads it piece by piece (and
/// again on retry), so it is copied here first.
enum UploadScratch {
    /// Emptied on first use each launch: anything still here was left by a run
    /// that ended mid-upload.
    static let root: URL = {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("ComposerUploads", isDirectory: true)
        try? FileManager.default.removeItem(at: url)
        return url
    }()

    /// Copy `url` into a folder of its own, so two picks with the same name never
    /// collide and the original name still reaches the Mac.
    static func copy(_ url: URL) throws -> URL {
        let dir = root.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let dest = dir.appendingPathComponent(url.lastPathComponent)
        try FileManager.default.copyItem(at: url, to: dest)
        return dest
    }

    static func discard(_ url: URL) {
        try? FileManager.default.removeItem(at: url.deletingLastPathComponent())
    }

    static func mime(_ url: URL) -> String {
        UTType(filenameExtension: url.pathExtension)?.preferredMIMEType ?? "application/octet-stream"
    }

    static func videoThumbnail(_ url: URL) async -> UIImage? {
        let generator = AVAssetImageGenerator(asset: AVURLAsset(url: url))
        generator.appliesPreferredTrackTransform = true
        generator.maximumSize = CGSize(width: 240, height: 240)
        guard let frame = try? await generator.image(at: .zero) else { return nil }
        return UIImage(cgImage: frame.image)
    }
}

/// A video from the photo library. The picker's own file is gone once the import
/// returns, so it is copied into the upload scratch folder on the way in.
struct PickedMovie: Transferable {
    let url: URL

    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(importedContentType: .movie) { received in
            PickedMovie(url: try UploadScratch.copy(received.file))
        }
    }
}
