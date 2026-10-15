import SwiftUI

/// The desktop's action color picker: no color, then the named accents as
/// swatches, each with a deeper shade. A custom color from the config file shows
/// as its own swatch so saving keeps it.
struct ActionColorPicker: View {
    @Binding var color: String

    private var deep: Bool { color.hasSuffix("-deep") }
    private var base: String { deep ? String(color.dropLast("-deep".count)) : color }
    private var custom: String? {
        color.isEmpty || ActionTint.names.contains(base) ? nil : color
    }

    var body: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 7), spacing: 6) {
            swatch(label: "No color", selected: color.isEmpty, value: "") {
                Image(systemName: "nosign")
                    .font(.system(size: 22))
                    .foregroundStyle(.secondary)
            }
            if let custom {
                swatch(label: "Custom color", selected: true, value: custom) {
                    Circle().fill(ActionTint(custom)?.accent ?? .gray)
                }
            }
            ForEach(ActionTint.names, id: \.self) { name in
                let value = deep ? "\(name)-deep" : name
                swatch(label: deep ? "Deep \(name)" : name.capitalized, selected: base == name, value: value) {
                    Circle().fill(ActionTint(value)?.accent ?? .gray)
                }
            }
        }
        Toggle("Deeper shade", isOn: Binding(
            get: { deep },
            set: { color = $0 ? "\(base)-deep" : base }
        ))
        .disabled(color.isEmpty || custom != nil)
    }

    private func swatch<Content: View>(label: String, selected: Bool, value: String,
                                       @ViewBuilder content: () -> Content) -> some View {
        Button {
            color = value
        } label: {
            content()
                .frame(width: 30, height: 30)
                .padding(3)
                .overlay(Circle().strokeBorder(selected ? Color.primary : .clear, lineWidth: 2))
                .frame(maxWidth: .infinity, minHeight: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}
