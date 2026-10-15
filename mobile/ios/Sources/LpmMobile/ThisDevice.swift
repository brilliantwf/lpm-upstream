import UIKit

/// What copy calls the device lpm Link runs on: "iPhone" or "iPad".
enum ThisDevice {
    static let noun = UIDevice.current.userInterfaceIdiom == .pad ? "iPad" : "iPhone"
}
