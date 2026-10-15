import SwiftUI
import UIKit

/// The project's control menu in the nav bar: a single "more" button whose native
/// menu holds Start/Stop and (when present) profiles. Actions have their own bolt
/// button beside it; per-service display and toggles live in the list's Services
/// section.
struct ProjectRunControl: View {
    @Environment(AppModel.self) private var model
    let project: Project
    let pending: Bool
    let changedCount: Int?
    let onStart: (_ profile: String) -> Void
    let onStop: () -> Void
    let onReviewChanges: () -> Void
    let onMemory: () -> Void
    let onNotes: () -> Void
    let onAutomations: () -> Void
    let onSwitchBranch: () -> Void
    let onCreatePr: () -> Void
    let onAuto: (_ kind: GitAutoKind) -> Void
    let onDiscard: () -> Void
    // On a terminal's screen Rename names that tab, not the project.
    let renamesTab: Bool
    let onRename: () -> Void
    let onTerminalSettings: () -> Void
    let onConfigure: () -> Void
    // The status waiting on its line; the host presents the prompt.
    @Binding var noteFor: WorkStatusChoice?
    // Accounts waiting on "only mine" before joining a list; the host asks.
    @Binding var claudeConsent: ClaudeConsentRequest?

    private var running: Bool { project.running }

    private var name: String { project.name }
    private var pulling: Bool { model.git.pulling.contains(name) }
    private var pushing: Bool { model.git.pushing.contains(name) }
    private var fetching: Bool { model.git.fetching.contains(name) }
    private var branch: String { model.git.snapshots[name]?.branch ?? "" }
    private var ghCli: Bool { model.git.snapshots[name]?.ghCli ?? false }
    private var autoRun: GitAutoRun? { model.gitAuto.runs[name] }
    private var unreadAutomations: Int {
        model.automations.filter { $0.unread > 0 && $0.runTargets.contains(name) }.count
    }

    var body: some View {
        Menu {
            // A YAML the Mac can't read has nothing to start, as on the desktop.
            if running || project.configError == nil {
                Button {
                    running ? onStop() : onStart(project.activeProfile)
                } label: {
                    Label(running ? "Stop" : "Start",
                          systemImage: running ? "stop.fill" : "play.fill")
                }
            }

            Button(action: onReviewChanges) {
                Label("Review Changes", systemImage: "checklist")
                if let changedCount, changedCount > 0 {
                    Text("\(changedCount) changed file\(changedCount == 1 ? "" : "s")")
                }
            }

            gitMenu

            // Memory is Mac-local: an SSH project's agents run on the other host,
            // where this Mac's session files mean nothing. Notes are stored per
            // project on the Mac either way, so they stay available.
            if !project.isRemote {
                Button(action: onMemory) {
                    Label("Memory", systemImage: "brain")
                }
            }
            Button(action: onNotes) {
                Label("Notes", systemImage: "note.text")
            }
            // Jobs run on the Mac in the project's own folder, so, as on the
            // desktop, an SSH project has none.
            if !project.isRemote {
                Button(action: onAutomations) {
                    Label("Automations", systemImage: "clock.arrow.circlepath")
                    if unreadAutomations > 0 {
                        Text("\(unreadAutomations) with new runs")
                    }
                }
                // An SSH project's agents run on the other host under its own login.
                ClaudeAccountMenu(project: project, consentFor: $claudeConsent)
            }
            if project.canHaveWorkStatus {
                WorkStatusMenu(project: project, noteFor: $noteFor)
            }

            if !project.profiles.isEmpty {
                Section("Profiles") {
                    ForEach(project.profiles) { p in
                        Button { onStart(p.name) } label: { profileLabel(p) }
                    }
                }
            }

            Divider()
            Button(action: onRename) {
                Label(renamesTab ? "Rename Tab…" : "Rename…", systemImage: "pencil")
            }
            Button(action: onConfigure) {
                Label("Configure…", systemImage: "slider.horizontal.3")
            }
            Button(action: onTerminalSettings) {
                Label("Terminal Settings", systemImage: "textformat.size")
            }
        } label: {
            if pending {
                ProgressView()
                    .controlSize(.small)
            } else {
                Image(systemName: "ellipsis")
                    .foregroundStyle(running ? .green : .primary)
            }
        }
    }

    /// The desktop's project Git submenu, adapted to mobile: sync ops that fire
    /// directly (disabled while in flight), then branch/PR navigation, then
    /// copy-branch and the destructive discard. Review Changes sits on the main
    /// menu, one level up.
    private var gitMenu: some View {
        Menu {
            Button { model.git.pull(name) } label: { Label("Pull", systemImage: "arrow.down") }
                .disabled(pulling)
            Button { model.git.push(name) } label: { Label("Push", systemImage: "arrow.up") }
                .disabled(pushing)
            Button { model.git.fetch(name) } label: { Label("Fetch", systemImage: "arrow.triangle.2.circlepath") }
                .disabled(fetching)
            AutoPullMenuItems(project: project)

            Divider()

            Button(action: onSwitchBranch) {
                Label("Switch Branch…", systemImage: "arrow.trianglehead.branch")
            }
            if ghCli {
                Button(action: onCreatePr) {
                    Label("Create Pull Request…", systemImage: "arrow.triangle.pull")
                }
            }

            Divider()

            autoButton(.commit)
            autoButton(.commitPush)
            if ghCli {
                autoButton(.pr)
            }

            Divider()

            Button { UIPasteboard.general.string = branch } label: {
                Label("Copy Branch Name", systemImage: "doc.on.doc")
            }
            .disabled(branch.isEmpty)
            Button(role: .destructive, action: onDiscard) {
                Label("Discard All Changes…", systemImage: "trash")
            }
            .disabled((changedCount ?? 0) == 0)
        } label: {
            Label("Git", systemImage: "arrow.trianglehead.branch")
        }
    }

    /// While a run is going every item reopens its progress sheet, so the one
    /// in flight reads as such; otherwise the commit items need changes to
    /// commit (Auto Create PR can still ship commits already made).
    private func autoButton(_ kind: GitAutoKind) -> some View {
        let running = autoRun?.running == true
        let inFlight = running && autoRun?.kind == kind
        return Button { onAuto(kind) } label: {
            Label(kind.title, systemImage: "sparkles")
            Text(inFlight ? "Running… tap to view" : kind.subtitle)
        }
        .disabled(!running && kind != .pr && (changedCount ?? 0) == 0)
    }

    @ViewBuilder
    private func profileLabel(_ p: Profile) -> some View {
        if running && project.activeProfile == p.name {
            Label(p.name, systemImage: "checkmark")
        } else {
            Text(p.name)
        }
    }

}

/// Hosts the project "more" menu on any screen: the toolbar button plus every
/// presentation its items drive (review push, git sheets, discard dialog,
/// action run flow, background-run sheet).
struct ProjectMenuHost: ViewModifier {
    @Environment(AppModel.self) private var model
    let project: Project
    // The terminal whose screen hosts the menu, if any.
    let terminal: TerminalInfo?
    // Run a terminal action and switch to the terminal it spawns.
    let onSpawnedTerminal: ((TerminalInfo) -> Void)?

    @State private var showChanges = false
    @State private var showMemory = false
    @State private var showNotes = false
    @State private var showAutomations = false
    @State private var showBranchSheet = false
    @State private var showPrSheet = false
    @State private var showAutoSheet = false
    @State private var confirmingDiscard = false
    @State private var showTerminalSettings = false
    @State private var showingConfig = false
    @State private var showActions = false
    // Action run flow: collect inputs (if any) → confirm (if flagged) → dispatch.
    @State private var runInputsFor: Action?
    @State private var runConfirmFor: Action?
    @State private var pendingInputValues: [String: String] = [:]
    @State private var activeBgRun: BackgroundRunInfo?
    @State private var isVisible = false
    // Rename flow: the text field's draft, seeded from the current label.
    @State private var renaming = false
    @State private var renameText = ""
    @State private var renamingTab: TabRename?
    @State private var noteFor: WorkStatusChoice?
    @State private var claudeConsent: ClaudeConsentRequest?

    // Changed-file count for the Review Changes menu item; nil until the snapshot
    // loads (or when the project isn't a git repo).
    private var changedCount: Int? {
        guard let s = model.git.snapshots[project.name], s.isRepo else { return nil }
        return s.files.count
    }
    private var pending: Bool { model.pendingRun[project.name] != nil }

    func body(content: Content) -> some View {
        content
            .navigationDestination(isPresented: $showChanges) { GitReviewView(project: project) }
            .navigationDestination(isPresented: $showMemory) { MemoryScreen(project: project) }
            .navigationDestination(isPresented: $showNotes) { NotesScreen(project: project.name) }
            .navigationDestination(isPresented: $showAutomations) { AutomationsView(project: project.name) }
            .sheet(isPresented: $showBranchSheet) {
                GitBranchSheet(project: project).environment(model)
            }
            .sheet(isPresented: $showPrSheet) {
                GitPrSheet(project: project).environment(model)
            }
            .sheet(isPresented: $showAutoSheet) {
                GitAutoSheet(project: project).environment(model)
            }
            .confirmationDialog(
                "Discard all changes?",
                isPresented: $confirmingDiscard,
                titleVisibility: .visible
            ) {
                Button("Discard changes", role: .destructive) { Haptics.warning(); model.git.discardAll(project.name) }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("This permanently discards every uncommitted change in this project. This can't be undone.")
            }
            // Gated on isVisible because several screens in one navigation stack
            // host this modifier — without it a single gitOpError would try to
            // present an alert on every one at once. The review screen owns its
            // own copy of this alert, hence the !showChanges gate too.
            .alert(
                model.git.opError[project.name]?.title ?? "",
                isPresented: Binding(
                    get: { isVisible && !showChanges && !showAutoSheet && model.git.opError[project.name] != nil },
                    set: { if !$0 { model.git.opError[project.name] = nil } }
                )
            ) {
                Button("OK", role: .cancel) { model.git.opError[project.name] = nil }
            } message: {
                Text(model.git.opError[project.name]?.message ?? "")
            }
            .gitNotice(project: project.name, isActive: isVisible && !showChanges)
            .sheet(item: $runInputsFor) { action in
                ActionInputsSheet(action: action) { values in afterInputs(action, values) }
            }
            .alert(
                runConfirmFor.map { "Run \($0.label)?" } ?? "Run action?",
                isPresented: Binding(
                    get: { runConfirmFor != nil },
                    set: { if !$0 { runConfirmFor = nil; pendingInputValues = [:] } }
                ),
                presenting: runConfirmFor
            ) { action in
                Button("Run") { runConfirmed(action) }
                Button("Cancel", role: .cancel) {}
            }
            .sheet(item: $activeBgRun) { run in
                BackgroundRunSheet(run: run).environment(model)
            }
            .sheet(isPresented: $showTerminalSettings) { TerminalSettingsSheet() }
            .sheet(isPresented: $showingConfig) {
                ProjectConfigView(project: project).environment(model)
            }
            .projectActionsSheet(project: project, isPresented: $showActions, onRun: beginRun)
            .tabRenameAlert($renamingTab)
            .alert("Rename project", isPresented: $renaming) {
                TextField("Project name", text: $renameText)
                Button("Cancel", role: .cancel) {}
                Button("Save") { model.renameProject(project, name: renameText) }
            } message: {
                Text("Set a display name for this project. Leave it blank to use the project id.")
            }
            .workStatusNotePrompt(project: project, noteFor: $noteFor)
            .claudeConsentPrompt($claudeConsent)
            .toolbar {
                if !project.actions.isEmpty {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button { showActions = true } label: {
                            Label("Actions", systemImage: "bolt")
                        }
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    ProjectRunControl(project: project,
                                      pending: pending,
                                      changedCount: changedCount,
                                      onStart: { model.startProject(project, profile: $0) },
                                      onStop: { model.stopProject(project) },
                                      onReviewChanges: { showChanges = true },
                                      onMemory: { showMemory = true },
                                      onNotes: { showNotes = true },
                                      onAutomations: { showAutomations = true },
                                      onSwitchBranch: { showBranchSheet = true },
                                      onCreatePr: { showPrSheet = true },
                                      onAuto: { kind in
                                          model.gitAuto.start(project.name, kind: kind)
                                          showAutoSheet = true
                                      },
                                      onDiscard: { confirmingDiscard = true },
                                      renamesTab: terminal != nil,
                                      onRename: rename,
                                      onTerminalSettings: { showTerminalSettings = true },
                                      onConfigure: { showingConfig = true },
                                      noteFor: $noteFor,
                                      claudeConsent: $claudeConsent)
                        .environment(model)
                }
            }
            .onAppear {
                isVisible = true
                model.git.load(project.name)
                if !project.isRemote { model.loadClaudeAccounts() }
            }
            .onDisappear { isVisible = false }
    }

    private func rename() {
        if let terminal {
            renamingTab = TabRename(terminal)
        } else {
            renameText = project.label
            renaming = true
        }
    }

    // Action run flow — mirrors the desktop gauntlet (inputs → confirm → dispatch).
    // Non-terminal actions run headlessly on the Mac with logs streamed back;
    // terminal/command actions relay to the Mac's terminal flow (confirmed:true so
    // the Mac doesn't re-prompt).
    private func beginRun(_ action: Action) {
        if !action.inputs.isEmpty { runInputsFor = action; return }
        if action.confirm { runConfirmFor = action; return }
        dispatch(action, inputValues: [:], deferred: false)
    }
    private func afterInputs(_ action: Action, _ values: [String: String]) {
        if action.confirm {
            pendingInputValues = values
            // The inputs sheet is dismissing in this same update; presenting the
            // alert now can be swallowed (one presentation at a time), so let the
            // sheet settle first — same guard as dispatch(deferred:).
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { runConfirmFor = action }
            return
        }
        dispatch(action, inputValues: values, deferred: true)
    }
    private func runConfirmed(_ action: Action) {
        let values = pendingInputValues
        pendingInputValues = [:]
        dispatch(action, inputValues: values, deferred: true)
    }
    private func dispatch(_ action: Action, inputValues: [String: String], deferred: Bool) {
        if action.runsInBackground {
            let runId = model.startBackgroundAction(project: project.name, action: action.name,
                                                    label: action.label, inputValues: inputValues)
            let present = { activeBgRun = model.backgroundRunInfo[runId] }
            // A run reached from a dismissing sheet/alert waits for it to settle
            // before this sheet is presented (SwiftUI can't stack two at once).
            if deferred { DispatchQueue.main.asyncAfter(deadline: .now() + 0.4, execute: present) }
            else { present() }
        } else {
            model.runAction(project.name, action: action.name, inputValues: inputValues,
                            confirmed: true, onSpawn: onSpawnedTerminal)
        }
    }
}

extension View {
    func projectMenuToolbar(project: Project, terminal: TerminalInfo? = nil,
                            onSpawnedTerminal: ((TerminalInfo) -> Void)? = nil) -> some View {
        modifier(ProjectMenuHost(project: project, terminal: terminal, onSpawnedTerminal: onSpawnedTerminal))
    }
}
