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
| JDK | Gradle daemon on **21** (`gradle/gradle-daemon-jvm.properties`, downloaded by Gradle if missing); bytecode target 17. Robolectric needs 21 for SDK 37 |
| Gradle wrapper | 9.8.0 (`gradle/wrapper`, distribution SHA-256 pinned) |
| Android Gradle Plugin | 9.4.1 (built-in Kotlin) |
| Kotlin / Compose compiler | 2.4.21 |
| Compose BOM | 2026.09.00 (needs compileSdk 37) |
| Hilt / KSP | 2.60.1 / 2.3.12 |
| SDK | compile/target 37, `minSdk 26` |
| Android CLI | `$ANDROID_HOME/cmdline-tools/latest/bin/android` (sdk, emulator, layout, screen, docs) |
| Emulator | 37.2.12, AVD `lpm_parity` (Pixel 9 Pro, API 36 Google Play image, arm64) |
| NDK | r30 (`ndk/30.0.16248370`), pinned in build-logic so native libraries are stripped |
| gomobile / gobind | `~/go/bin` — `gomobile bind` of `tailnet/node` → AAR verified (arm64, 83 s, 16 KB-aligned) |
| Maestro | 2.11.0 — drives both the iOS simulator and the emulator |
| Agent skills | `.claude/skills/{android-cli,navigation-3,edge-to-edge,camerax,testing-setup}` (official, via `android skills add`) |

`node mobile/parity/parity.js doctor` re-checks all of it.

```sh
cd mobile/android
./gradlew spotlessApply                               # format (ktlint + Compose rules)
./gradlew check assembleRelease -PwarningsAsErrors=true  # what CI runs
./run.sh                                              # install + launch on a device/emulator
node ../parity/parity.js all --open                   # both apps, all flows, side-by-side report
```

## Architecture

Google's [recommendations for Android architecture](https://developer.android.com/topic/architecture/recommendations)
(`android docs fetch kb://android/topic/architecture/recommendations`), applied to a
port. **Port behavior, not structure**: the screens, strings and protocol match iOS;
the code is idiomatic Android.

- **Data layer.** Repositories are the single source of truth and expose `Flow` /
  `StateFlow`. One session per active Mac owns the connection (real client or
  `DemoServer`) and routes inbound frames into the repositories. iOS's `AppModel` is
  one observable object: its state goes to repositories, its screen logic to ViewModels.
- **UI layer.** One `@HiltViewModel` per screen exposing `uiState: StateFlow<…UiState>`
  (`stateIn(viewModelScope, WhileSubscribed(5_000), …)`), collected with
  `collectAsStateWithLifecycle()`. User actions are ViewModel calls; results come back
  as state, never as one-off events. Reusable composables take state and lambdas, no
  ViewModels. No domain layer up front; add a use case only when two ViewModels need
  the same logic.
- **DI.** Hilt. Interfaces with `Default…` implementations bound with `@Binds`; fakes
  for tests (prefer fakes to mocks). Demo Mode is a transport chosen at runtime, not a
  test double.
- **Navigation.** Single activity, Navigation 3: `@Serializable` `NavKey`s in
  `:core:navigation`, entries contributed by each feature, ViewModels scoped to entries
  (`lifecycle-viewmodel-navigation3`). Predictive back is on (targetSdk 36+). iOS ships
  on iPad, so tablets and foldables get a list-detail scene (Material 3 adaptive), not a
  stretched phone layout.
- **UI.** Compose + Material 3 themed from the iOS palette, edge-to-edge. Strings live
  in each module's `res/values/strings.xml`, copied verbatim. Every icon-only control
  has a `contentDescription`; touch targets are at least 48 dp.
- **Concurrency.** Coroutines and Flow; dispatchers injected; long-lived work in an
  injected application scope, never `GlobalScope`.
- **Storage and security.** Preferences DataStore with the iOS key names; credentials
  encrypted with an Android Keystore AES-GCM key (not the deprecated
  EncryptedSharedPreferences). Cleartext is off (`network_security_config.xml`) because
  every connection is `wss://` with a pinned certificate. App data is excluded from cloud
  backup and device transfer: Keystore keys never leave the device, so copied credentials
  could not be decrypted on a new phone.

### Modules

Modularized by layer and feature; create a module when its milestone starts.

| Module | Kind | Holds |
|---|---|---|
| `:app` | application | `LpmApplication`, `MainActivity`, the `NavDisplay` |
| `:core:model` | JVM | data types shared by every layer (`MacRecord`, projects, services, jobs…) |
| `:core:protocol` | JVM | wire frames (kotlinx.serialization), client interface, OkHttp WebSocket + pin |
| `:core:demo` | JVM | `DemoServer` and the seed |
| `:core:data` | Android | repositories, Mac session, DataStore, Keystore, NSD discovery, connectivity |
| `:core:navigation` | JVM | `NavKey`s |
| `:core:designsystem` | Android | theme, iOS palette, shared components (exists) |
| `:core:web` | Android | WebView host, `WebViewAssetLoader`, the `window.webkit` shim |
| `:core:testing` | Android | fakes, test rules, Hilt test runner |
| `:core:push`, `:core:tailnet` | Android | FCM, built-in Tailscale |
| `:feature:<name>` | Android | screens + ViewModels, one per row of the feature map |

### Build setup

- `build-logic/` convention plugins: `lpm.android.application`, `lpm.android.library`
  (namespace from the module path), `lpm.android.compose`, `lpm.android.feature`
  (library + Compose + Hilt + lifecycle + Navigation 3 + `:core:designsystem`),
  `lpm.hilt` (KSP; Android or JVM), `lpm.jvm.library` (Kotlin JVM + lint),
  `lpm.spotless`. A new feature module's build file is
  `plugins { alias(libs.plugins.lpm.android.feature) }`.
- Versions only in `gradle/libs.versions.toml`; projects referenced as
  `projects.core.data`.
- Strict by default: lint warnings are errors (only version-bump nags are off), Kotlin
  warnings are errors with `-PwarningsAsErrors=true` (CI), ktlint (`android_studio`
  style + Compose rules) on every module.
- Release: R8 with resource shrinking; App Bundle for Play.
- CI: `.github/workflows/android.yml` runs `check assembleRelease` on changes to
  `mobile/android` or `mobile/shared`.

## Testing

Per the `testing-setup` skill:

| Layer | Where | Tools |
|---|---|---|
| Unit | `src/test`, JVM | JUnit 4, kotlinx-coroutines-test, Turbine; ViewModels asserted through `uiState.value`; protocol tests fed with PROTOCOL.md frames |
| UI behavior | `src/test`, Robolectric | Compose test APIs with `androidx.compose.ui.test.junit4.v2.createComposeRule` (v1 is deprecated); semantic matchers before test tags; state restoration |
| Screenshot | — | Compose Preview Screenshot Testing as an AGP test suite once AGP 9.5 is stable (9.4.1 has only the deprecated standalone plugin) |
| End to end | `mobile/parity` | Maestro flows on both apps — the acceptance test for every milestone |

Coverage: `createDebugUnitTestCoverageReport` (Android modules), `jacocoTestReport`
(JVM modules). Workarounds kept in build-logic, each with the reason: Robolectric 4.17
needs `--add-exports java.base/jdk.internal.access` on SDK 37 (robolectric#11434), and
Compose's test library still pulls Espresso 3.5, which breaks on API 34+, so androidx.test
is pinned to current.

### iOS → Android

| iOS | Android |
|---|---|
| `LpmMobileApp` + `ContentView` (Views.swift), `NavigationPath` | `MainActivity` + `NavDisplay` over a Navigation 3 back stack |
| `AppModel` (@Observable, 3.1k lines, `AppModel+*.swift`) | repositories in `:core:data` + one ViewModel per screen |
| `LpmProtocol.swift` (`Wire.*` builders, `Inbound.parse`) | `:core:protocol`: kotlinx.serialization, same message names and fields |
| `LpmClient` (URLSessionWebSocketTask, TLS pin/TOFU, ping, backoff, 16 MB frames, send queue) | `:core:protocol`: OkHttp WebSocket, `X509TrustManager` checking only the SHA-256 pin |
| `DemoServer` + `Demo*.swift` (in-process fake Mac answering 126 `t` values) | `:core:demo`: same fake, same seed — **ported first**, it backs every parity flow |
| SwiftUI sheets / menus / alerts | `ModalBottomSheet` / `DropdownMenu` / `AlertDialog`, same titles and button order |
| WKWebView + `web/terminal.html` (xterm.js), `window.webkit.messageHandlers` | `:core:web`: WebView + `WebViewAssetLoader` on `assets/web/` (from `mobile/shared/web`) and a JS shim exposing `window.webkit.messageHandlers.*` so `terminal.html`/`markdown.js` run unchanged |
| Keychain (`cx.lpm.mobile`, `cx.lpm.mobile.push`) | Keystore AES-GCM key encrypting a DataStore entry |
| UserDefaults | Preferences DataStore with the same key names |

## Feature map

Port order is top to bottom within each milestone below. Lines are iOS sizes.

| Feature → Android module | iOS files | Lines |
|---|---|---|
| Core shell, model, wire, client → `:app`, `:core:model`, `:core:protocol`, `:core:data` | LpmMobileApp, AppModel(+…), LpmProtocol, LpmClient, Views, SessionAlerts, GenerationTimeout, ThisDevice | 7,983 |
| Demo Mode → `:core:demo` | DemoServer, DemoWorld, DemoProjects, DemoTerminals, DemoGit, DemoGitAuto, DemoJobs, DemoConfig, DemoMemory, DemoNotes(+Fixtures), DemoStats, DemoLimits, DemoClaudeAccounts, DemoAutoPull, DemoServiceLogs, DemoUploads | 5,883 |
| Pairing → `:feature:pairing` | PairingView, EnterCodeView, QRScannerView, ApprovalPairingSheet, NearbyMacsView, AwayFromHomeCard, PairedSummarySheet, IdentityCheckSheet, Keychain | 1,461 |
| Connection / discovery → `:core:data`, `:feature:connection` | MacDiscovery, HostProbe, NetworkWatcher, LinkStore, AppModel+Link, ConnectionIssue, ConnectionSheet(s), ConnectionStatusBar, EditEndpointView, AddressKind, ProjectSnapshots | 2,312 |
| Machines → `:feature:mac` | MacRecord, MacStore, MacSwitcherMenu, MacTitle, ManageMacsView, MacUnpair, MacUpdate*, MachineHeader/Importer/Section, MachineVisitScreen, AppModel+Visit, RemoteMachine, PairDirectlyMenuItems | 1,947 |
| Projects → `:feature:projects` | ProjectDetailView, ProjectMenu, ProjectAgents, ProjectTerminalDeck/Row, AddProjectSheet, DirBrowserView, DuplicateOptionsView, ProjectConfigView, ProjectConfigErrorView, YamlConfigEditorSheet, ProfileEditorSheet, WorkStatus* | 2,717 |
| Services → `:feature:services` | ServiceEditorSheet, ServiceLogsSheet | 510 |
| Terminal → `:feature:terminal`, `:core:web` | WebTerminalView, WKWebView+NoAccessoryBar, TerminalScreens, TerminalTitle, TerminalTheme, TerminalSettingsSheet, TerminalEndedBanner, TabRow, TabPeek, KeyboardSteadyFrame, WindowTouchObserver | 1,004 |
| Composer → `:feature:composer` | TerminalComposer, ComposerStore(+Files), ComposerAttachments, ComposerTabStrip, ComposerActionsSheet, ComposerVariantsSheet, HistoryScreen, ChunkedUpload, UploadScratch, PlainPunctuation, HardwareReturn | 2,830 |
| Actions → `:feature:actions` | ActionColorPicker, ActionEditorSheet, ActionInputsSheet, ActionPlacement, ActionTint, ActionZoneView, ActionZones, DesktopActionButton, ProjectActionsSheet(+Host), BackgroundRunSheet | 1,840 |
| Activity → `:feature:activity` | ActivityBuild, ActivityChrome, ActivityModel, ActivityRowViews, ActivityScreen, AgentStateStyle | 1,322 |
| Automations → `:feature:automations` | AutomationConversationView, AutomationDetailRows, AutomationDetailView, AutomationEditorSheet, AutomationFormat, AutomationViews | 2,283 |
| Git → `:feature:git` | GitReviewView, GitReviewStore, GitBranchSheet, GitAutoSheet, GitAutoStore, GitNotice, GitOpError, DiffHighlighter, DiffRendering, AgentAskSheet | 3,006 |
| Notes → `:feature:notes` | NotesScreen, NotesChatView, NotesComposer, NotesMessageRow, NotesAttachmentView, NotesSearchStore, NotesStore, NotesTypes, NoteAttachmentStore | 1,736 |
| Memory → `:feature:memory` | MemoryScreen, MemorySessionView, MemoryCreateSheet, MemoryEditorSheet, MemoryStore, MemoryTypes, ComposerMemorySheet | 1,167 |
| Files / preview → `:feature:files`, `:core:web` | FileFetch, FilePreviewContent, FilePreviewKind, FilePreviewPlatformViews, FilePreviewSheet, MarkdownWebView, MarkdownImageLoader, MarkdownLink | 854 |
| Usage / stats → `:feature:usage` | Usage*, StatsFormat, StatsScreen | 2,722 |
| Claude accounts → `:feature:claudeaccount` | ClaudeAccountConsent, ClaudeAccountMenu, ClaudeAccountModel, ClaudeInUse, AppModel+ClaudeAccount | 493 |
| Auto-pull → `:feature:autopull` | AutoPullMenu, AutoPullModel, AppModel+AutoPull | 149 |
| Speech (read aloud) → `:feature:speech` | SpeechBar, SpeechClip, SpeechPrefs, SpeechSettingsView, SpeechStore, SpeechText | 1,171 |
| Settings → `:feature:settings` | SettingsSheet, AppearanceMode | 112 |
| Push → `:core:push` | AppDelegate, PushRegistrar, NotificationSettingsSheet, Shared/PushKey, Shared/PushPayload, NotificationService | 661 |
| Built-in Tailscale → `:core:tailnet` | BuiltInTailscale, TailnetSettingsView (+ Go `tailnet/`) | 452 |
| Shared UI → `:core:designsystem` | CodeEditorTextView, DarkKeyboard, ElapsedText, FollowingScrollView, Haptics, KeyboardObserver, MarkdownText, SkeletonViews, SwipeDownDismissesKeyboard, SafariView | 791 |

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
| Embedded Go `LpmTailnet` static lib | `gomobile bind` AAR from a new `tailnet/android` module (own `go.mod` with `replace lpm.cx/tailnet => ..` and `tool golang.org/x/mobile/cmd/gobind`, so the desktop sidecar's module stays clean). Android 11+ blocks netlink, so register a netmon interface getter fed from `ConnectivityManager` (the role `defaultroute_darwin.go` plays on iOS). The `.so` is already 16 KB page-aligned, as Play requires for native code |

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
| 0 | Scaffold, toolchain, harness, build-logic, CI (done) | 01–11 pass on iOS; 12 waits on the iOS exit-demo fix |
| 1 | Shared demo seed (iOS first), `:core:model`, `:core:protocol`, `:core:demo`, `:core:data`, `:core:navigation` | — (unit tests against PROTOCOL.md frames) |
| 2 | Pairing screen, Enter a Code, demo entry/exit | 01, 02, 12 |
| 3 | Projects list, project detail, project menu, services | 03, 04, 05 |
| 4 | Terminal (WebView + shim) and composer | 06 |
| 5 | Activity, Automations, Stats, Usage, Settings | 07–11 |
| 6 | Git review, Notes, Memory, Actions, config editors, file preview, Claude accounts, auto-pull, speech, machines | new flows per screen |
| 7 | Real Mac: OkHttp client + pin, Bonjour, QR, approve-on-Mac, reconnect | flows against the running desktop app (emulator reaches the Mac at `10.0.2.2`; no Bonjour through emulator NAT, use Enter a Code) |
| 8 | Push (FCM) | needs the items below |
| 9 | Built-in Tailscale | `tailnet/android` AAR |

## Decisions

1. **Push: FCM data messages**, the only push channel on Play devices. The payload stays
   the AES-GCM blob iOS decrypts; the app decrypts it and posts the notification on
   channels matching the `lpm.notify.*` toggles. The protocol gets a platform-neutral
   registration (`pushToken` + `platform: "apns" | "fcm"`) beside `apnsToken`, which
   stays for existing iOS clients, and the relay (`website/app/api/push`) gets an FCM
   HTTP v1 sender: a desktop + website + PROTOCOL.md change in milestone 8.
2. **Play: App Bundle, Play App Signing**, with a separate upload key kept outside the
   repo. Package `cx.lpm.mobile`, the iOS bundle id; it can't change after the first upload.
3. **Demo seed: one source of truth.** The static seed (projects, git changes, notes,
   memory, history, accounts, usage, stats) moves to `mobile/shared/demo/*.json`, read by
   both apps; each keeps only its `DemoServer` logic. iOS goes first, guarded by flows
   01–11 and the App Store screenshot build, which renders demo data. Part of milestone 1.
4. **Icon-only controls** (list below) get the same accessible name on both apps in the
   change that ports their screen; iOS gains VoiceOver labels.
5. **Tailscale ABIs: `arm64-v8a` + `x86_64`** (emulators, ChromeOS). Play delivers only
   the device's ABI from the bundle.
6. **Desktop links**: a Play link joins the App Store one in
   `desktop/frontend/src/mobile/links.ts` at release.

Needs an account:

- **Firebase project** (`google-services.json`) before milestone 8.
- **Play Console.** A personal account created after 13 Nov 2023 must run a closed
  test with at least 12 opted-in testers for 14 consecutive days before production;
  organization accounts are exempt. Start the closed test once milestone 3 works.

## iOS controls with no accessible name

Flows can't reach these by text until both apps label them the same way (adding
the label on iOS also helps VoiceOver): composer "+" menu, send, SpecialKeyBar
arrows/paste/dismiss-keyboard, attachment remove, composer tab close, rewrite run
and variant steppers, memory-session eye, history new-folder; Notes new chat
(`square.and.pencil`), error-banner close, attach menu, download; Git review jump
menu, per-file select / viewed / menu, new branch; QR scanner close. The project "…"
menu already reads "More" through SwiftUI's default symbol label.
