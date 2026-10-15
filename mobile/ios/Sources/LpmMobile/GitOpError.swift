import Foundation

/// A failed Git operation as the alert shows it: which operation failed — the
/// desktop's "Pull failed" — over what Git or the Mac said about it.
struct GitOpError: Equatable {
    let title: String
    let message: String
}
