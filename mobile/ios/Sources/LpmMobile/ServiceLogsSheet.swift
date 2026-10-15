import SwiftUI

/// A service's recent pane output while the service runs — the mobile counterpart
/// of the desktop's service pane. The Mac pushes it as it changes
/// (`serviceLogsWatch`); one that predates that is polled every 3s instead. The
/// toolbar carries the service's Start/Stop toggle.
struct ServiceLogsSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let project: String
    let service: Service

    private var live: Project? { model.projects.first(where: { $0.name == project }) }
    private var running: Bool {
        guard let p = live, p.running else { return false }
        return p.services.contains(where: { $0.name == service.name })
    }
    private var pending: Bool { model.pendingServiceToggle[project]?[service.name] != nil }
    // Pane index from the last `services` discovery; nil until it loads (or while
    // the service is stopped), so the poll loop keeps re-discovering.
    private var paneIndex: Int? {
        model.services[project]?.first(where: { $0.name == service.name })?.paneIndex
    }
    private var key: String? { paneIndex.map { model.serviceLogsKey(project, $0) } }
    private var text: String { key.flatMap { model.serviceLogsResult[$0] } ?? "" }
    private var feed: Feed { Feed(running: running, paneIndex: paneIndex, ready: model.connection == .ready) }
    private var fetchError: String? { key.flatMap { model.serviceLogsError[$0] } }

    var body: some View {
        NavigationStack {
            FollowingScrollView(follow: text) {
                VStack(alignment: .leading, spacing: 0) {
                    if let fetchError, text.isEmpty {
                        Text(fetchError)
                            .foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding()
                    } else if text.isEmpty {
                        HStack(spacing: 8) {
                            if running { ProgressView().controlSize(.small) }
                            Text(running ? "Waiting for output…" : "This service isn't running.")
                                .foregroundStyle(.secondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding()
                    } else {
                        Text(text)
                            .font(.system(.caption, design: .monospaced))
                            .textSelection(.enabled)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding()
                    }
                }
            }
            .safeAreaInset(edge: .top, spacing: 0) { statusBar }
            .navigationTitle(service.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if pending {
                        ProgressView().controlSize(.small)
                    } else if running {
                        Button("Stop", role: .destructive) { model.toggleService(project, service: service.name) }
                    } else {
                        Button("Start") { model.toggleService(project, service: service.name) }
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationBackground(Color(.systemBackground))
        .preferredColorScheme(.dark)
        // Restarts as the service starts or stops, its pane is found, or the link
        // comes back — the Mac forgets a dropped connection's watch.
        .task(id: feed) {
            guard feed.running, feed.ready else { return }
            guard let paneIndex = feed.paneIndex else {
                while !Task.isCancelled {
                    model.loadServices(project)
                    try? await Task.sleep(nanoseconds: 3_000_000_000)
                }
                return
            }
            if model.canWatchServiceLogs {
                model.watchServiceLogs(project, paneIndex: paneIndex, true)
                while !Task.isCancelled { try? await Task.sleep(nanoseconds: 3_600_000_000_000) }
                model.watchServiceLogs(project, paneIndex: paneIndex, false)
                return
            }
            while !Task.isCancelled {
                model.fetchServiceLogs(project, paneIndex: paneIndex)
                try? await Task.sleep(nanoseconds: 3_000_000_000)
            }
        }
        .onDisappear {
            if let key { model.consumeServiceLogs(key) }
        }
    }

    private struct Feed: Equatable {
        let running: Bool
        let paneIndex: Int?
        let ready: Bool
    }

    private var statusBar: some View {
        HStack(spacing: 8) {
            Circle().fill(statusColor).frame(width: 8, height: 8)
            Text(statusText).font(.subheadline.weight(.medium)).lineLimit(1)
            if service.port > 0 {
                Text(":\(String(service.port))")
                    .font(.subheadline.monospacedDigit())
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: 8)
            if pending { ProgressView().controlSize(.small) }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(.bar)
    }

    private var statusColor: Color {
        if pending { return .blue }
        return running ? .green : Color(.systemGray3)
    }
    private var statusText: String {
        if pending { return running ? "Stopping…" : "Starting…" }
        return running ? "Running" : "Stopped"
    }
}
