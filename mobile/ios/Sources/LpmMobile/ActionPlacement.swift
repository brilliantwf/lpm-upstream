import Foundation

/// Where an action appears in the project's controls: the top or the bottom
/// bar, or a zone in either, at one of its layers when it has them.
enum ActionPlacement: Hashable, Identifiable {
    case header, footer
    case zone(String, layer: String?)

    var id: Self { self }

    /// From the action's `display` and `layer` as written. A layer naming none
    /// of the zone's is its first, where the desktop shows it; a zone the
    /// project doesn't have stays as written, so saving doesn't move it.
    init(display: String, layer: String?, zones: [Zone]) {
        switch display {
        case "", "button", "header": self = .header
        case "footer": self = .footer
        default:
            if let zone = zones.first(where: { $0.name == display }) {
                self = .zone(display, layer: zone.layer(named: layer ?? ""))
            } else {
                self = .zone(display, layer: layer.flatMap { $0.isEmpty ? nil : $0 })
            }
        }
    }

    var layer: String? {
        if case .zone(_, let layer) = self { return layer }
        return nil
    }

    func title(in zones: [Zone]) -> String {
        switch self {
        case .header: return "Top bar"
        case .footer: return "Bottom bar"
        case .zone(let name, let layer):
            guard let zone = zones.first(where: { $0.name == name }) else { return "Zone \u{201C}\(name)\u{201D}" }
            guard let index = zone.layers.firstIndex(where: { $0.name == layer }) else { return zone.label }
            return "\(zone.label) \u{203A} \(zone.layers[index].title(at: index))"
        }
    }

    /// Which bar a zone placement sits in, for the picker's hint.
    func hint(in zones: [Zone]) -> String? {
        guard case .zone(let name, _) = self, let zone = zones.first(where: { $0.name == name }) else { return nil }
        return "In the \(zone.label) zone of the \(zone.isFooter ? "bottom" : "top") bar."
    }

    /// The bars, then every zone and layer (zonePlacements in zoneLayers.ts)
    /// that shows wherever the action's file applies: a project file's action
    /// can go in any zone, a repo or global one only in a zone from a file as
    /// wide, so the other projects it shows in still have the zone.
    static func options(zones: [Zone], source: String, current: ActionPlacement) -> [ActionPlacement] {
        let reach = ["project", "repo", "global"]
        let floor = reach.firstIndex(of: source) ?? 0
        let placements = zones
            .filter { (reach.firstIndex(of: $0.source) ?? 0) >= floor }
            .flatMap { zone -> [ActionPlacement] in
                zone.layers.isEmpty
                    ? [.zone(zone.name, layer: nil)]
                    : zone.layers.map { .zone(zone.name, layer: $0.name) }
            }
        let options: [ActionPlacement] = [.header, .footer] + placements
        return options.contains(current) ? options : options + [current]
    }
}
