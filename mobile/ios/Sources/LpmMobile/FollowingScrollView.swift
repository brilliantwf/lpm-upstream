import SwiftUI

private let followBottomID = "following-bottom"
// How far above the end still counts as reading it: a couple of lines.
private let followSlack: CGFloat = 40

/// A vertical scroll view over live output that keeps the newest line in view
/// while the reader is at the end, and leaves them where they are once they
/// scroll back to read, so the next poll doesn't drag them down again.
struct FollowingScrollView<Content: View, Key: Equatable>: View {
    /// Changes whenever new output lands.
    let follow: Key
    /// Turning true takes the reader back to the end wherever they are, as
    /// sending a reply does.
    var returnToEnd = false
    @ViewBuilder let content: () -> Content

    @State private var atEnd = true
    @State private var viewportHeight: CGFloat = 0
    @State private var contentTop: CGFloat?

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(spacing: 0) {
                    content()
                    Color.clear.frame(height: 1).id(followBottomID)
                }
                // Only scrolling moves the content's top, so only scrolling
                // decides where the reader is; new output just grows the content
                // and can't count as them leaving the end.
                .onGeometryChange(for: CGRect.self) { $0.frame(in: .scrollView) } action: { frame in
                    defer { contentTop = frame.minY }
                    guard let top = contentTop, frame.minY != top, viewportHeight > 0 else { return }
                    atEnd = frame.maxY - viewportHeight <= followSlack
                }
            }
            .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { viewportHeight = $0 }
            .onAppear { proxy.scrollTo(followBottomID, anchor: .bottom) }
            .onChange(of: follow) { _, _ in
                guard atEnd else { return }
                withAnimation(.easeOut(duration: 0.15)) { proxy.scrollTo(followBottomID, anchor: .bottom) }
            }
            .onChange(of: returnToEnd) { _, now in
                guard now else { return }
                atEnd = true
                withAnimation(.easeOut(duration: 0.2)) { proxy.scrollTo(followBottomID, anchor: .bottom) }
            }
        }
    }
}
