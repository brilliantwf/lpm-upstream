import SwiftUI
import UIKit

/// The key bar's first key: copies the agent's last answer as the markdown it
/// wrote. Touch and hold for an earlier answer. Dimmed while the agent is
/// producing the next one, rather than hidden, so the keys beside it never move.
struct CopyAnswerKey: View {
    @Environment(AppModel.self) private var model
    let source: AnswerSource

    private enum Phase: Equatable { case idle, loading, copied, empty, failed }

    @State private var phase: Phase = .idle
    @State private var reset: Task<Void, Never>?
    @State private var showEarlier = false

    private var disabled: Bool { model.connection != .ready || model.isAgentWorking(source) }

    var body: some View {
        Menu {
            Button { showEarlier = true } label: {
                Label("Copy an Earlier Answer…", systemImage: "clock.arrow.circlepath")
            }
        } label: {
            keyFace
        } primaryAction: {
            copyLast()
        }
        .disabled(disabled)
        .opacity(disabled ? 0.4 : 1)
        .accessibilityLabel("Copy last answer")
        .accessibilityValue(phaseText)
        .sheet(isPresented: $showEarlier) {
            CopyAnswerSheet(source: source) { flash(.copied) }
                .environment(model)
        }
        .onDisappear { reset?.cancel() }
    }

    private var keyFace: some View {
        HStack(spacing: 5) {
            ZStack {
                if phase == .loading {
                    ProgressView().controlSize(.mini)
                } else {
                    Image(systemName: icon)
                }
            }
            .frame(width: 14)
            Text(phaseText)
        }
        .font(.system(size: 13, weight: .semibold, design: .rounded))
        .foregroundStyle(phase == .copied ? SwiftUI.Color.green : SwiftUI.Color.blue)
        .frame(minWidth: 82, minHeight: 32)
        .padding(.horizontal, 6)
        .background(SwiftUI.Color.blue.opacity(0.18))
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
        .animation(.easeOut(duration: 0.15), value: phase)
    }

    private var icon: String {
        switch phase {
        case .copied: return "checkmark"
        case .failed: return "exclamationmark.triangle"
        default: return "doc.on.doc"
        }
    }

    private var phaseText: String {
        switch phase {
        case .idle, .loading: return "Copy"
        case .copied: return "Copied"
        case .empty: return "None yet"
        case .failed: return "Failed"
        }
    }

    private func copyLast() {
        guard phase != .loading else { return }
        reset?.cancel()
        phase = .loading
        Task {
            do {
                let text = try await model.recentAnswers(source, limit: 1).first?.text ?? ""
                if text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    Haptics.warning()
                    flash(.empty)
                } else {
                    UIPasteboard.general.string = text
                    Haptics.success()
                    flash(.copied)
                }
            } catch {
                Haptics.error()
                flash(.failed)
            }
        }
    }

    private func flash(_ next: Phase) {
        phase = next
        reset?.cancel()
        reset = Task {
            try? await Task.sleep(for: .seconds(1.5))
            if !Task.isCancelled { phase = .idle }
        }
    }
}
