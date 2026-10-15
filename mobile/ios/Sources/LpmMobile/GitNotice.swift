import SwiftUI

/// A finished Git op's short confirmation — the desktop's "Pulled", "Pushed",
/// "Fetched" and "Committed successfully" toasts. Every notice is distinct, so
/// the same op finishing twice in a row shows again.
struct GitNotice: Equatable {
    let id = UUID()
    let text: String
}

/// The project's current Git notice, at the top of the screen like the terminal
/// banners. `isActive` is gated like the op-error alert, so of the screens stacked
/// for one project only the visible one shows it.
private struct GitNoticeBanner: ViewModifier {
    @Environment(AppModel.self) private var model
    let project: String
    let isActive: Bool

    private var notice: GitNotice? { isActive ? model.git.notice[project] : nil }

    func body(content: Content) -> some View {
        content
            .overlay(alignment: .top) {
                if let notice {
                    HStack(spacing: 8) {
                        Image(systemName: "checkmark.circle.fill")
                        Text(notice.text).lineLimit(1)
                    }
                    .font(.footnote)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(.black.opacity(0.6), in: Capsule())
                    .overlay(Capsule().strokeBorder(.white.opacity(0.15)))
                    .environment(\.colorScheme, .dark)
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                    .id(notice.id)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .allowsHitTesting(false)
                }
            }
            .animation(.easeOut(duration: 0.2), value: notice)
    }
}

extension View {
    func gitNotice(project: String, isActive: Bool) -> some View {
        modifier(GitNoticeBanner(project: project, isActive: isActive))
    }
}
