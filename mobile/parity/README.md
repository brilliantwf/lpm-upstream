# lpm Link parity

One set of [Maestro](https://maestro.dev) flows runs on the iOS app (the reference)
in an iPhone simulator and on the Android app in an emulator, at the same time. Every
`takeScreenshot` step is paired in a report, iOS on the left, Android on the right,
with each flow's pass/fail per platform.

```sh
node mobile/parity/parity.js doctor            # check tools
node mobile/parity/parity.js up                # boot "lpm Parity iPhone" (iPhone 18 Pro) + AVD lpm_parity (Pixel 9 Pro)
node mobile/parity/parity.js build             # Debug builds of both apps
node mobile/parity/parity.js install
node mobile/parity/parity.js test --open       # all flows, both devices, report
node mobile/parity/parity.js test 05-project-menu --only android
node mobile/parity/parity.js all --open        # up + build + install + test
node mobile/parity/parity.js down              # shut down what `up` booted
```

`up` opens Device Hub (Xcode 27's simulator app) and the emulator window, so a flow
can be watched playing on both phones at once; `--headless` skips the windows. Both
devices get a 9:41 status bar, full battery, light mode and US English. Runs and
reports go to `~/Library/Caches/lpm-parity/runs/<time>/` (`runs/latest` links the
newest).

## Flows

`flows/NN-name.yaml`, one screen or behavior each, every flow starting from a fresh
launch (`clearState`). Most enter Demo Mode first (`subflows/enter-demo.yaml`): the
in-app sample Mac, so no desktop app or network is needed and both apps show the
same seed.

Writing one:
1. Select by **visible text only** — the same string on both platforms is the point.
   Maestro text selectors are regexes (`"More.*"`).
2. Find the names with `maestro --device <udid|serial> hierarchy` on the iOS app.
   Icon-only buttons carry SwiftUI's default symbol names ("More", "Add", "Forward");
   the Android control needs the same `contentDescription`.
3. Make it pass on iOS first (`--only ios`). A flow failing on iOS is a broken flow;
   failing on Android is a parity gap.
4. `takeScreenshot: N-name` at each state worth comparing.
5. Unavoidable platform differences go in a `runFlow: when: platform: iOS|Android`
   block, and in the allowed-differences list in `mobile/android/PORTING.md`.
