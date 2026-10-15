import SwiftUI

/// Where the content's text ends, kept current by the content and read when the
/// keyboard moves — a plain reference, so a cursor move doesn't re-render the
/// screen.
final class TextExtent {
    /// From the content's top to the bottom of its last line of text.
    var bottom: CGFloat = .infinity
    /// Whether giving the content its rows back pulls older text down from above,
    /// so the text still ends at the bottom.
    var fillsOnGrow = true
}

/// Keeps its content at the height it has with the keyboard closed and clips it,
/// so the keyboard slides over the content instead of resizing it every
/// animation frame. Text that reaches the bottom stays pinned there, its top
/// sliding out of sight; text that ends above the keyboard stays pinned to the
/// top, so it never leaves the screen. The content is handed how much of its top
/// and bottom sits out of sight, which grows only once the keyboard has settled
/// and drops as soon as it starts closing — one reflow each way.
struct KeyboardSteadyFrame<Content: View>: View {
    @ObservedObject var keyboard: KeyboardObserver
    let extent: TextExtent
    @ViewBuilder let content: (_ hidden: EdgeInsets) -> Content
    @State private var height: CGFloat = 0
    @State private var restHeight: CGFloat = 0
    @State private var hidden: CGFloat = 0
    @State private var pinTop = false

    var body: some View {
        Color.clear
            .overlay(alignment: pinTop ? .top : .bottom) {
                content(EdgeInsets(top: pinTop ? 0 : hidden, leading: 0, bottom: pinTop ? hidden : 0, trailing: 0))
                    .frame(height: restHeight > 0 ? restHeight : nil)
            }
            .clipped()
            .onGeometryChange(for: CGFloat.self, of: { $0.size.height }) { newHeight in
                if newHeight < height {
                    pinTop = extent.bottom <= newHeight
                } else if newHeight > height {
                    pinTop = !extent.fillsOnGrow
                }
                height = newHeight
                restHeight = max(restHeight, newHeight)
                hidden = min(hidden, restHeight - newHeight)
                // SwiftUI lays out for the keyboard before the keyboard
                // notification reaches the observer, so the rest waits for it.
                DispatchQueue.main.async(execute: settle)
            }
            .onChange(of: keyboard.settledHeight) { settle() }
            .onChange(of: keyboard.isShown) { settle() }
    }

    private func settle() {
        if !keyboard.isShown { restHeight = height }
        if keyboard.height == keyboard.settledHeight { hidden = restHeight - height }
    }
}
