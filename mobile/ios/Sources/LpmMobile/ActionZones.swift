import Foundation

/// A framed group of buttons in the header or footer row (mirrors the Rust
/// ZoneInfo). Placement only: an action joins it with `display: <name>`, and
/// one of its layers with `layer:`.
struct Zone: Identifiable {
    let name: String
    let label: String
    let rows: Int
    let isFooter: Bool
    let position: Double?
    let layers: [ZoneLayer]
    let collapsed: Bool
    /// The file that declares it: project | repo | global.
    let source: String

    var id: String { name }

    init(_ o: [String: Any]) {
        name = o["name"] as? String ?? ""
        let lbl = o["label"] as? String ?? ""
        label = lbl.isEmpty ? name : lbl
        rows = min(max(o["rows"] as? Int ?? 1, 1), 3)
        isFooter = o["display"] as? String == "footer"
        position = (o["position"] as? NSNumber)?.doubleValue
        layers = (o["layers"] as? [[String: Any]] ?? []).map(ZoneLayer.init)
        collapsed = o["collapsed"] as? Bool ?? false
        source = o["source"] as? String ?? "project"
    }

    /// The layer an action's `layer` puts it in: the one it names, else the
    /// first. nil for a zone without layers.
    func layer(named layer: String) -> String? {
        guard let first = layers.first else { return nil }
        return layers.first { $0.name == layer }?.name ?? first.name
    }

    /// The zone's button lists, each layer's in layer order, or one for the
    /// whole zone when it has no layers.
    fileprivate func lists(_ placed: [String: [Action]]) -> [ZoneList] {
        if layers.isEmpty {
            return [ZoneList(key: name, layer: nil, title: label, actions: placed[name] ?? [])]
        }
        return layers.enumerated().map { index, layer in
            let key = Zone.listKey(name, layer.name)
            return ZoneList(key: key, layer: layer, title: layer.title(at: index), actions: placed[key] ?? [])
        }
    }

    fileprivate static func listKey(_ zone: String, _ layer: String?) -> String {
        layer.map { "\(zone)/\($0)" } ?? zone
    }
}

/// A named sub-group of a zone (mirrors the Rust LayerInfo).
struct ZoneLayer: Identifiable {
    let name: String
    /// Empty when the config gives it none.
    let label: String
    let position: Double?

    var id: String { name }

    init(_ o: [String: Any]) {
        name = o["name"] as? String ?? ""
        label = o["label"] as? String ?? ""
        position = (o["position"] as? NSNumber)?.doubleValue
    }

    /// A layer without a label is named by its place in the zone.
    func title(at index: Int) -> String {
        label.isEmpty ? "Layer \(index + 1)" : label
    }
}

/// One of a zone's button lists: a layer, or the whole zone when it has none.
struct ZoneList: Identifiable {
    let key: String
    let layer: ZoneLayer?
    let title: String
    let actions: [Action]

    var id: String { key }
}

/// One spot in the header or footer row: a button, or a zone with its lists.
enum ActionRowItem: Identifiable {
    case action(Action)
    case zone(Zone, [ZoneList])

    var id: String {
        switch self {
        case .action(let action): return action.name
        case .zone(let zone, _): return "@zone/\(zone.name)"
        }
    }
}

/// The project's actions laid out as the desktop's header and footer rows show
/// them (buildActionsModel in actionsLayoutModel.ts): a button whose display
/// names no known zone sits in the header, and one naming no layer of its zone
/// in the first layer. A zone without a button is left out, since the phone
/// can't drop one into it.
struct ActionRows {
    let header: [ActionRowItem]
    let footer: [ActionRowItem]
    let menu: [Action]

    init(actions: [Action], zones: [Zone]) {
        let byName = Dictionary(zones.map { ($0.name, $0) }, uniquingKeysWith: { first, _ in first })
        var placed: [String: [Action]] = [:]
        var header: [Action] = [], footer: [Action] = [], menu: [Action] = []
        for action in actions {
            switch action.display {
            case "", "header", "button": header.append(action)
            case "footer": footer.append(action)
            case "menu": menu.append(action)
            default:
                if let zone = byName[action.display] {
                    placed[Zone.listKey(zone.name, zone.layer(named: action.layer)), default: []].append(action)
                } else {
                    header.append(action)
                }
            }
        }
        self.header = Self.row(header, zones.filter { !$0.isFooter }, placed)
        self.footer = Self.row(footer, zones.filter(\.isFooter), placed)
        self.menu = menu
    }

    private static func row(_ actions: [Action], _ zones: [Zone], _ placed: [String: [Action]]) -> [ActionRowItem] {
        typealias Entry = (name: String, position: Double?, item: ActionRowItem)
        let buttons: [Entry] = actions.map { ($0.name, $0.position, .action($0)) }
        let frames: [Entry] = zones.compactMap { zone in
            let lists = zone.lists(placed)
            guard lists.contains(where: { !$0.actions.isEmpty }) else { return nil }
            return (zone.name, zone.position, .zone(zone, lists))
        }
        return (buttons + frames).sorted { precedes($0, $1) }.map(\.item)
    }

    /// Positioned entries first, then by name compared bytewise, as the Mac
    /// sorts them (comparePosition in actionsLayoutModel.ts).
    private static func precedes(_ a: (name: String, position: Double?, item: ActionRowItem),
                                 _ b: (name: String, position: Double?, item: ActionRowItem)) -> Bool {
        switch (a.position, b.position) {
        case let (x?, y?) where x != y: return x < y
        case (.some, nil): return true
        case (nil, .some): return false
        default: return a.name.utf8.lexicographicallyPrecedes(b.name.utf8)
        }
    }
}

/// Which layer each zone shows, per project, kept on this iPhone as the desktop
/// keeps its own.
@Observable
final class ZoneLayerMemory {
    static let shared = ZoneLayerMemory()

    private static let key = "zoneOpenLayers"
    private var open = UserDefaults.standard.dictionary(forKey: ZoneLayerMemory.key) as? [String: String] ?? [:]

    /// The open list among `lists`: the remembered layer, else the first.
    func openIndex(project: String, zone: String, lists: [ZoneList]) -> Int {
        let stored = open["\(project)/\(zone)"]
        return lists.firstIndex { $0.layer?.name == stored } ?? 0
    }

    func show(_ layer: String, project: String, zone: String) {
        open["\(project)/\(zone)"] = layer
        UserDefaults.standard.set(open, forKey: Self.key)
    }
}
