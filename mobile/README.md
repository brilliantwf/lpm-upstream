# lpm Link

The phone companion to lpm: pairs with lpm on a Mac (or a Linux host) and mirrors
its projects, services, terminals and agents. Every command still runs on the Mac;
the phone is a display and input client.

```
mobile/
├── PROTOCOL.md     # the wire contract both apps implement (matches remote.rs)
├── shared/web/     # xterm.js terminal + markdown pages, bundled by both apps' WebViews
├── shared/demo/    # Demo Mode sample files (images) both apps ship
├── ios/            # iOS app (SwiftUI) — the reference; see ios/README.md
├── android/        # Android app (Kotlin, Jetpack Compose) — see android/PORTING.md
└── parity/         # side-by-side tests: one set of Maestro flows on both apps
```

| | Build | Run |
|---|---|---|
| iOS | `cd mobile/ios && xcodegen generate && open LpmMobile.xcodeproj` | `mobile/ios/run.sh` (physical iPhone) |
| Android | `cd mobile/android && ./gradlew assembleDebug` | `mobile/android/run.sh` (device or emulator) |
| Both, compared | `node mobile/parity/parity.js all --open` | iPhone simulator + Android emulator, report with paired screenshots |

`node mobile/parity/parity.js doctor` checks the whole toolchain.
