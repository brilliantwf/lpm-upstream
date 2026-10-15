import SwiftUI

/// Rows for the projects list's "…" menu that pair this iPhone with a machine it
/// otherwise opens through the live Mac: one removed from this iPhone, or one
/// whose automatic pairing failed.
struct PairDirectlyMenuItems: View {
    @Environment(AppModel.self) private var model

    private var sections: [MachineSection] {
        model.machineSections.filter {
            switch $0.pending {
            case .removed?, .failed?: return true
            default: return false
            }
        }
    }

    var body: some View {
        let sections = self.sections
        if !sections.isEmpty {
            Divider()
            ForEach(sections) { section in
                Button {
                    model.machineImporter.add(section.machine.slug)
                } label: {
                    Text("Pair Directly with \(section.machine.name)")
                    Text(detail(section))
                    Image(systemName: "link")
                }
            }
        }
    }

    private func detail(_ section: MachineSection) -> String {
        if case .failed(let why)? = section.pending { return why }
        return "Get its notifications and open it while this Mac sleeps"
    }
}
