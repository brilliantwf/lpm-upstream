# Porting lpm Link to Android

The iOS app (`mobile/ios`, SwiftUI, 213 files / ~45k lines) is the reference. The
Android app must look and behave the same: same screens, strings, fields,
defaults, placement and colors, speaking the same protocol (`mobile/PROTOCOL.md`).
Parity is proven with `mobile/parity` — the same Maestro flows run on both apps
side by side (see `mobile/parity/README.md`).

## Ground rules

- **Strings are copied verbatim from Swift**, including `…`, `—`, `·` and curly
  quotes. Parity flows select by visible text, so a reworded label is a failing test.
- **Every icon-only control gets the same accessible name as on iOS**
  (`contentDescription` / `semantics { contentDescription }`). SwiftUI gives SF
  Symbols default names — `ellipsis` → "More", `plus` → "Add", `chevron.right` →
  "Forward" — so match what `maestro hierarchy` shows on iOS, not the Swift source.
- **No ids in tests.** The iOS app has no `accessibilityIdentifier`s and reports SF
  Symbol names as ids; flows use text only.
- **Same colors, no Material You dynamic color.** Build the theme from the iOS
  palette (terminal themes, action tints, agent-state colors, usage meters).
- **Allowed platform differences** (keep the list short and write each one here):
  system back instead of the nav-bar back button; Android permission dialogs;
  "phone" where iOS copy says "iPhone"/"iPad"; FCM instead of APNs.
- **Same persisted keys and semantics** as iOS (table below), so behavior and
  defaults match.
- Desktop changes that touch the phone now update both apps (AGENTS.md).

## Toolchain (installed and verified on this Mac)

| Piece | Version / location |
|---|---|
| JDK | 17 (Homebrew `openjdk@17`) |
| Gradle wrapper | 9.8.0 (`gradle/wrapper`, distribution SHA-256 pinned) |
| Android Gradle Plugin | 9.4.1 (built-in Kotlin) |
| Kotlin / Compose compiler | 2.4.20 |
| Compose BOM | 2026.09.00 (needs compileSdk 37) |
| SDK | platform `android-37.0`, build-tools 36, `minSdk 26` |
| Android CLI | `$ANDROID_HOME/cmdline-tools/latest/bin/android` (sdk, emulator, layout, screen, docs) |
| Emulator | 37.2.12, AVD `lpm_parity` (Pixel 9 Pro, API 36 Google Play image, arm64) |
| NDK | r30 (`ndk/30.0.16248370`) — for the built-in Tailscale `.so` |
| gomobile / gobind | `~/go/bin` — `gomobile bind` of `tailnet/node` → AAR verified (arm64, 83 s) |
| Maestro | 2.11.0 — drives both the iOS simulator and the emulator |
| Agent skills | `.claude/skills/{android-cli,navigation-3,edge-to-edge,camerax,testing-setup}` (official, via `android skills add`) |

`node mobile/parity/parity.js doctor` re-checks all of it.

```sh
cd mobile/android && ./gradlew assembleDebug    # build
mobile/android/run.sh                           # install + launch on a device/emulator
node mobile/parity/parity.js all --open         # both apps, all flows, side-by-side report
```

## Architecture mapping

| iOS | Android |
|---|---|
| `LpmMobileApp` + `ContentView` (Views.swift) | `MainActivity` + root composable; Navigation 3 back stack mirroring `NavigationPath` |
| `AppModel` (@Observable, 3.1k lines, split by `AppModel+*.swift`) | one `AppModel` state holder (`StateFlow`s) split into the same extension areas as files |
| `LpmProtocol.swift` (`Wire.*` builders, `Inbound.parse`) | `core/protocol`: kotlinx.serialization, same message names and fields |
| `LpmClient` (URLSessionWebSocketTask, TLS pin/TOFU, ping, backoff, 16 MB frames, send queue) | `core/protocol`: OkHttp WebSocket, custom `X509TrustManager` checking only the SHA-256 pin |
| `DemoServer` + `Demo*.swift` (in-process fake Mac answering 126 `t` values) | `demo/`: same fake, same seed — **ported first**, it backs every parity flow |
| SwiftUI sheets / menus / alerts | `ModalBottomSheet` / `DropdownMenu` / `AlertDialog`, same titles and button order |
| WKWebView + `web/terminal.html` (xterm.js), `window.webkit.messageHandlers` | WebView + `WebViewAssetLoader` on `assets/web/` (from `mobile/shared/web`) and a JS shim exposing `window.webkit.messageHandlers.*` so `terminal.html`/`markdown.js` run unchanged |
| Keychain (`cx.lpm.mobile`, `cx.lpm.mobile.push`) | Android Keystore AES-GCM key encrypting a DataStore entry (no extension, nothing to share) |
| UserDefaults | Preferences DataStore with the same key names |

## Feature map

Port order is top to bottom within each milestone below. Lines are iOS sizes.

| Feature → Android package | iOS files | Lines |
|---|---|---|
| Core shell, model, wire, client → `core/`, `model/` | LpmMobileApp, AppModel(+…), LpmProtocol, LpmClient, Views, SessionAlerts, GenerationTimeout, ThisDevice | 7,983 |
| Demo Mode → `demo/` | DemoServer, DemoWorld, DemoProjects, DemoTerminals, DemoGit, DemoGitAuto, DemoJobs, DemoConfig, DemoMemory, DemoNotes(+Fixtures), DemoStats, DemoLimits, DemoClaudeAccounts, DemoAutoPull, DemoServiceLogs, DemoUploads | 5,883 |
| Pairing → `feature/pairing` | PairingView, EnterCodeView, QRScannerView, ApprovalPairingSheet, NearbyMacsView, AwayFromHomeCard, PairedSummarySheet, IdentityCheckSheet, Keychain | 1,461 |
| Connection / discovery → `feature/connection` | MacDiscovery, HostProbe, NetworkWatcher, LinkStore, AppModel+Link, ConnectionIssue, ConnectionSheet(s), ConnectionStatusBar, EditEndpointView, AddressKind, ProjectSnapshots | 2,312 |
| Machines → `feature/mac` | MacRecord, MacStore, MacSwitcherMenu, MacTitle, ManageMacsView, MacUnpair, MacUpdate*, MachineHeader/Importer/Section, MachineVisitScreen, AppModel+Visit, RemoteMachine, PairDirectlyMenuItems | 1,947 |
| Projects → `feature/projects` | ProjectDetailView, ProjectMenu, ProjectAgents, ProjectTerminalDeck/Row, AddProjectSheet, DirBrowserView, DuplicateOptionsView, ProjectConfigView, ProjectConfigErrorView, YamlConfigEditorSheet, ProfileEditorSheet, WorkStatus* | 2,717 |
| Services → `feature/services` | ServiceEditorSheet, ServiceLogsSheet | 510 |
| Terminal → `feature/terminal` | WebTerminalView, WKWebView+NoAccessoryBar, TerminalScreens, TerminalTitle, TerminalTheme, TerminalSettingsSheet, TerminalEndedBanner, TabRow, TabPeek, KeyboardSteadyFrame, WindowTouchObserver | 1,004 |
| Composer → `feature/composer` | TerminalComposer, ComposerStore(+Files), ComposerAttachments, ComposerTabStrip, ComposerActionsSheet, ComposerVariantsSheet, HistoryScreen, ChunkedUpload, UploadScratch, PlainPunctuation, HardwareReturn | 2,830 |
| Actions → `feature/actions` | ActionColorPicker, ActionEditorSheet, ActionInputsSheet, ActionPlacement, ActionTint, ActionZoneView, ActionZones, DesktopActionButton, ProjectActionsSheet(+Host), BackgroundRunSheet | 1,840 |
| Activity → `feature/activity` | ActivityBuild, ActivityChrome, ActivityModel, ActivityRowViews, ActivityScreen, AgentStateStyle | 1,322 |
| Automations → `feature/automations` | AutomationConversationView, AutomationDetailRows, AutomationDetailView, AutomationEditorSheet, AutomationFormat, AutomationViews | 2,283 |
| Git → `feature/git` | GitReviewView, GitReviewStore, GitBranchSheet, GitAutoSheet, GitAutoStore, GitNotice, GitOpError, DiffHighlighter, DiffRendering, AgentAskSheet | 3,006 |
| Notes → `feature/notes` | NotesScreen, NotesChatView, NotesComposer, NotesMessageRow, NotesAttachmentView, NotesSearchStore, NotesStore, NotesTypes, NoteAttachmentStore | 1,736 |
| Memory → `feature/memory` | MemoryScreen, MemorySessionView, MemoryCreateSheet, MemoryEditorSheet, MemoryStore, MemoryTypes, ComposerMemorySheet | 1,167 |
| Files / preview → `feature/files` | FileFetch, FilePreviewContent, FilePreviewKind, FilePreviewPlatformViews, FilePreviewSheet, MarkdownWebView, MarkdownImageLoader, MarkdownLink | 854 |
| Usage / stats → `feature/usage` | Usage*, StatsFormat, StatsScreen | 2,722 |
| Claude accounts → `feature/claudeaccount` | ClaudeAccountConsent, ClaudeAccountMenu, ClaudeAccountModel, ClaudeInUse, AppModel+ClaudeAccount | 493 |
| Auto-pull → `feature/autopull` | AutoPullMenu, AutoPullModel, AppModel+AutoPull | 149 |
| Speech (read aloud) → `feature/speech` | SpeechBar, SpeechClip, SpeechPrefs, SpeechSettingsView, SpeechStore, SpeechText | 1,171 |
| Settings → `feature/settings` | SettingsSheet, AppearanceMode | 112 |
| Push → `core/push` | AppDelegate, PushRegistrar, NotificationSettingsSheet, Shared/PushKey, Shared/PushPayload, NotificationService | 661 |
| Built-in Tailscale → `core/tailnet` | BuiltInTailscale, TailnetSettingsView (+ Go `tailnet/`) | 452 |
| Shared UI → `ui/` | CodeEditorTextView, DarkKeyboard, ElapsedText, FollowingScrollView, Haptics, KeyboardObserver, MarkdownText, SkeletonViews, SwipeDownDismissesKeyboard, SafariView | 791 |

## Platform services

| iOS | Android |
|---|---|
| APNs + Notification Service Extension (decrypts AES-GCM payload, rewrites text) | FCM **data** messages decrypted in `FirebaseMessagingService`, notification posted by the app; `POST_NOTIFICATIONS` on 13+ |
| `NWBrowser` Bonjour `_lpm._tcp` (TXT `id`, `name`, `v`, `rp`, `dev`) | `NsdManager` + `MulticastLock` |
| `NWPathMonitor` | `ConnectivityManager.NetworkCallback` |
| AVFoundation QR scanner | CameraX + ML Kit barcode scanning (`camerax` skill) |
| AVSpeechSynthesizer / AVAudioPlayer / Now Playing | `TextToSpeech`, Media3 + `MediaSession`, `mediaPlayback` foreground service |
| SFSafariViewController | Custom Tabs |
| PhotosPicker / camera / fileImporter | Photo Picker, `TakePicture`, `OpenMultipleDocuments` |
| ShareLink / QuickLook / AVKit | `ACTION_SEND` + FileProvider; in-app renderers + Media3 |
| Swift Charts (Stats) | Compose Canvas |
| Haptics | `HapticFeedback` |
| Keyboard handling (steady terminal frame, hardware Return sends, no smart punctuation) | `WindowInsets.ime`, `onPreviewKeyEvent`, `KeyboardOptions(autoCorrectEnabled = false)` |
| Embedded Go `LpmTailnet` static lib | `gomobile bind` AAR from a new `tailnet/android` module (own `go.mod` with `replace lpm.cx/tailnet => ..` and `tool golang.org/x/mobile/cmd/gobind`, so the desktop sidecar's module stays clean). Android 11+ blocks netlink, so register a netmon interface getter fed from `ConnectivityManager` (the role `defaultroute_darwin.go` plays on iOS) |

Manifest needs: `INTERNET`, `ACCESS_NETWORK_STATE`, `CHANGE_WIFI_MULTICAST_STATE`,
`CAMERA`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`; cleartext is not
needed (the Mac speaks `wss` with a pinned self-signed cert). Not used on iOS, so not
needed here: background tasks, widgets, live activities, app intents, speech
recognition, biometrics.

## Persisted state (same keys)

| iOS | Key | Contents |
|---|---|---|
| Keychain `cx.lpm.mobile` | `device-credential.<localId>` | `{"deviceId","token"}` per Mac |
| Keychain `cx.lpm.mobile` | `cert-pin.<localId>` | lowercase hex SHA-256 of the Mac's leaf cert |
| Keychain `cx.lpm.mobile.push` | `push-key` | 32 random bytes, one per phone, registered with every Mac |
| UserDefaults | `lpm.macs`, `lpm.activeMacId`, `lpm.machineImports` | saved Macs (`MacRecord` JSON), active Mac, machine-import ledger |
| UserDefaults | `lpm.push.registered.<localId>` | last acknowledged push registration |
| UserDefaults | `lpm.notify.enabled` / `.waiting` / `.done` / `.error` / `.automation.started` / `.automation.done` / `.automation.error` | missing = on, except `automation.started` (off) |
| UserDefaults | `appearanceMode`, `terminalFontSize` (12, 8–24), `terminalTheme` (`claude-dark`) | appearance |
| UserDefaults | `speech.engine`, `speech.voiceId`, `speech.rate` | read aloud |
| UserDefaults | `builtInTailscale.enabled`, `pairing.usesTailscaleApp` | Tailscale |
| UserDefaults | `activity.groupByProject`, `automation.scheduleOpen`, `automation.setupOpen`, `filePreviewMarkdownSource`, `actionPrimaryLastUsed`, `zoneOpenLayers`, `macUpdate.dismissed.<id>` | UI memory |
| Files | `Caches/project-lists/<localId>.json`; `ApplicationSupport/tailnet/` (no backup) | offline project list; tailnet state (`noBackupFilesDir`) |

## Demo Mode

The only entry is **"No Mac nearby? Try the demo"** on the root pairing screen
(`model.enterDemo()`); "Exit" on the "Demo — sample projects" banner, "Exit Demo" in
the Mac menu, or "Add a machine…" leaves it. Nothing persists. `DemoServer` receives
every frame `LpmClient` would send and answers with protocol frames through the normal
inbound path, after `ready` from serverId `demo-mac` / "Demo Mac". The seed:
projects `storefront` (running; web, api running, worker stopped; Terminal 1 + a
scripted Claude session), `api-gateway`, `mobile-app`, folder "Client Work" (`blog`,
`design-system`); git changes on `storefront`; jobs "Nightly tests" and "Weekly
dependency check"; notes, memory, history, Claude accounts, usage and stats.

Port the server and seed as-is so the same flows see the same data. Seed dates are
relative to now, so a few numbers shift between runs.

## Milestones

Each one: write or extend the parity flows first and make them pass on iOS, then
build Android until the same flows pass there.

| # | Scope | Parity flows |
|---|---|---|
| 0 | Scaffold, toolchain, harness (done) | 01–12 pass on iOS |
| 1 | Protocol types, client interface, `DemoServer` + seed, `AppModel` core | — (unit tests against PROTOCOL.md frames) |
| 2 | Pairing screen, Enter a Code, demo entry/exit | 01, 02, 12 |
| 3 | Projects list, project detail, project menu, services | 03, 04, 05 |
| 4 | Terminal (WebView + shim) and composer | 06 |
| 5 | Activity, Automations, Stats, Usage, Settings | 07–11 |
| 6 | Git review, Notes, Memory, Actions, config editors, file preview, Claude accounts, auto-pull, speech, machines | new flows per screen |
| 7 | Real Mac: OkHttp client + pin, Bonjour, QR, approve-on-Mac, reconnect | flows against the running desktop app (emulator reaches the Mac at `10.0.2.2`; no Bonjour through emulator NAT, use Enter a Code) |
| 8 | Push (FCM) | needs the items below |
| 9 | Built-in Tailscale | `tailnet/android` AAR |

## Needs a decision or an account

1. **Push**: a Firebase project and `google-services.json`; the relay
   (`website/app/api/push`, `website/lib/apns.ts`) and `apnsToken` in PROTOCOL.md /
   `remote.rs` are APNs-only — an FCM token message and relay route are a desktop +
   website change.
2. **Play Console** account, upload key, and confirming the package name
   `cx.lpm.mobile`.
3. **Desktop copy**: the desktop's phone pairing UI links only the App Store
   (`desktop/frontend/src/mobile/links.ts`); a Play Store link comes with release.
4. **Demo seed duplication**: porting `Demo*.swift` to Kotlin means editing the seed
   twice for every phone-facing change. The alternative is moving the static seed into
   `mobile/shared/demo/*.json` read by both apps (an iOS refactor).
5. **ABIs** for the Tailscale `.so`: `arm64-v8a` + `x86_64` suggested (the AAR is
   ~40 MB per ABI unstripped; build with `-ldflags="-s -w"`).

## iOS controls with no accessible name

Flows can't reach these by text until both apps label them the same way (adding
the label on iOS also helps VoiceOver): composer "+" menu, send, SpecialKeyBar
arrows/paste/dismiss-keyboard, attachment remove, composer tab close, rewrite run
and variant steppers, memory-session eye, history new-folder; Notes new chat
(`square.and.pencil`), error-banner close, attach menu, download; Git review jump
menu, per-file select / viewed / menu, new branch; QR scanner close. The project "…"
menu already reads "More" through SwiftUI's default symbol label.
