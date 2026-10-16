#!/usr/bin/env node
// lpm Link parity harness: the same Maestro flows run on the iOS app (the
// reference) in an iPhone simulator and on the Android app in an emulator, and
// every screenshot step is paired in one report so the apps compare side by side.
const fs = require("fs");
const os = require("os");
const path = require("path");
const { execFileSync, spawn, spawnSync } = require("child_process");

const MOBILE = path.resolve(__dirname, "..");
const FLOWS = path.join(__dirname, "flows");
const CACHE = path.join(os.homedir(), "Library/Caches/lpm-parity");
const STATE = path.join(CACHE, "booted.json");
const SDK = process.env.ANDROID_HOME || path.join(os.homedir(), "Library/Android/sdk");
const APP_ID = "cx.lpm.mobile";

const IOS = { name: "lpm Parity iPhone", type: "iPhone 18 Pro" };
const ANDROID = {
  avd: "lpm_parity",
  device: "pixel_9_pro",
  image: "system-images;android-36;google_apis_playstore;arm64-v8a",
};
const IOS_APP = path.join(CACHE, "ios-dd/Build/Products/Debug-iphonesimulator/LpmMobile.app");
const APK = path.join(MOBILE, "android/app/build/outputs/apk/debug/app-debug.apk");
// Ad-hoc signed so the simulator honours the keychain entitlement and pairing survives a relaunch.
const IOS_SIGNING = ["CODE_SIGN_IDENTITY=-", "CODE_SIGN_STYLE=Manual", "DEVELOPMENT_TEAM=",
  "PROVISIONING_PROFILE_SPECIFIER=", "CODE_SIGNING_REQUIRED=NO"];

const sdkTool = (...p) => path.join(SDK, ...p);
const ADB = sdkTool("platform-tools/adb");
const ANDROID_CLI = sdkTool("cmdline-tools/latest/bin/android");
const AVDMANAGER = sdkTool("cmdline-tools/latest/bin/avdmanager");
const run = (cmd, args, opts = {}) => execFileSync(cmd, args, { encoding: "utf8", stdio: "pipe", ...opts });
const simctl = (...args) => run("xcrun", ["simctl", ...args]);
const adb = (serial, ...args) => run(ADB, ["-s", serial, ...args]);
const has = (cmd) => spawnSync("sh", ["-c", `command -v ${cmd}`]).status === 0;

fs.mkdirSync(CACHE, { recursive: true });

function readState() {
  try { return JSON.parse(fs.readFileSync(STATE, "utf8")); } catch { return {}; }
}
function writeState(patch) {
  fs.writeFileSync(STATE, JSON.stringify({ ...readState(), ...patch }));
}

// ---- doctor ----------------------------------------------------------------

function doctor() {
  const gobin = path.join(run("go", ["env", "GOPATH"]).trim(), "bin");
  const ndk = fs.existsSync(sdkTool("ndk")) ? fs.readdirSync(sdkTool("ndk")) : [];
  const platforms = fs.existsSync(sdkTool("platforms")) ? fs.readdirSync(sdkTool("platforms")) : [];
  const checks = [
    ["Xcode", has("xcodebuild"), "install Xcode"],
    ["XcodeGen", has("xcodegen"), "brew install xcodegen"],
    ["Go", has("go"), "brew install go"],
    ["gomobile", fs.existsSync(path.join(gobin, "gomobile")), "go install golang.org/x/mobile/cmd/gomobile@latest golang.org/x/mobile/cmd/gobind@latest"],
    ["JDK 17+", spawnSync("java", ["-version"]).status === 0, "brew install openjdk@17"],
    ["Android SDK", fs.existsSync(SDK), "install Android Studio, or set ANDROID_HOME"],
    ["Android CLI (cmdline-tools)", fs.existsSync(ANDROID_CLI), "Android Studio → SDK Manager → Android SDK Command-line Tools"],
    ["Android platform 37", platforms.some((p) => p.startsWith("android-37")), `${ANDROID_CLI} sdk install "platforms;android-37.0"`],
    ["Android emulator", fs.existsSync(sdkTool("emulator/emulator")), `${ANDROID_CLI} sdk install emulator`],
    ["Emulator system image", fs.existsSync(sdkTool(...ANDROID.image.split(";"))), `${ANDROID_CLI} sdk install "${ANDROID.image}"`],
    ["Android NDK (built-in Tailscale)", ndk.length > 0, `${ANDROID_CLI} sdk install ndk`],
    ["Maestro", has("maestro"), "brew tap mobile-dev-inc/tap && brew install mobile-dev-inc/tap/maestro"],
    [`AVD ${ANDROID.avd}`, avdExists(), "node mobile/parity/parity.js up (creates it)"],
    [`Simulator "${IOS.name}"`, !!findSim(), "node mobile/parity/parity.js up (creates it)"],
  ];
  let ok = true;
  for (const [name, pass, fix] of checks) {
    console.log(`${pass ? "✓" : "✗"} ${name}${pass ? "" : `  → ${fix}`}`);
    ok &&= pass;
  }
  if (!ok) process.exitCode = 1;
}

// ---- devices ---------------------------------------------------------------

function findSim() {
  const { devices } = JSON.parse(simctl("list", "devices", "available", "-j"));
  for (const r of Object.keys(devices).filter((r) => /iOS/.test(r)).sort().reverse()) {
    const d = devices[r].find((x) => x.name === IOS.name);
    if (d) return d;
  }
  return null;
}

function createSim() {
  const { devicetypes } = JSON.parse(simctl("list", "devicetypes", "-j"));
  const type = devicetypes.find((t) => t.name === IOS.type);
  if (!type) throw new Error(`no simulator device type "${IOS.type}" (xcrun simctl list devicetypes)`);
  const { runtimes } = JSON.parse(simctl("list", "runtimes", "-j"));
  const runtime = runtimes.filter((r) => r.isAvailable && r.platform === "iOS").pop();
  if (!runtime) throw new Error("no iOS simulator runtime installed (Xcode → Settings → Components)");
  simctl("create", IOS.name, type.identifier, runtime.identifier);
  return findSim();
}

function bootSim(udid) {
  try { simctl("boot", udid); } catch (e) {
    if (!/current state: Booted/.test(String(e.stderr))) throw e;
  }
  simctl("bootstatus", udid, "-b");
}

function iosUp({ headless }) {
  const sim = findSim() || createSim();
  if (sim.state !== "Booted") writeState({ iosBooted: true });
  bootSim(sim.udid);
  let locale = "";
  try { locale = simctl("spawn", sim.udid, "defaults", "read", "-g", "AppleLocale").trim(); } catch {}
  if (locale !== "en_US") {
    simctl("spawn", sim.udid, "defaults", "write", "-g", "AppleLocale", "en_US");
    simctl("spawn", sim.udid, "defaults", "write", "-g", "AppleLanguages", "-array", "en-US");
    simctl("spawn", sim.udid, "defaults", "write", "-g", "AppleICUForce24HourTime", "-bool", "false");
    simctl("shutdown", sim.udid);
    bootSim(sim.udid);
  }
  simctl("status_bar", sim.udid, "override", "--time", "9:41", "--dataNetwork", "wifi", "--wifiMode", "active",
    "--wifiBars", "3", "--cellularMode", "active", "--cellularBars", "4", "--operatorName", "",
    "--batteryState", "discharging", "--batteryLevel", "100");
  simctl("ui", sim.udid, "appearance", "light");
  // Xcode 27 shows simulators in Device Hub (there is no Simulator.app).
  if (!headless) spawnSync("open", ["-b", "com.apple.dt.Devices"]);
  console.log(`iOS: ${IOS.name} (${sim.udid})`);
  return sim.udid;
}

function avdExists() {
  return fs.existsSync(path.join(os.homedir(), ".android/avd", `${ANDROID.avd}.avd`));
}

function createAvd() {
  run(AVDMANAGER, ["create", "avd", "-n", ANDROID.avd, "-k", ANDROID.image, "-d", ANDROID.device], { input: "no\n" });
  const config = path.join(os.homedir(), ".android/avd", `${ANDROID.avd}.avd/config.ini`);
  const lines = fs.readFileSync(config, "utf8").split("\n").filter((l) => !/^(hw\.keyboard|hw\.ramSize)=/.test(l));
  fs.writeFileSync(config, [...lines.filter(Boolean), "hw.keyboard=yes", "hw.ramSize=4G", ""].join("\n"));
}

function emulatorSerial() {
  const serials = run(ADB, ["devices"]).split("\n").map((l) => l.split("\t")).filter(([s, st]) => /^emulator-/.test(s) && st === "device").map(([s]) => s);
  return serials.find((s) => {
    try { return adb(s, "emu", "avd", "name").split(/\r?\n/)[0].trim() === ANDROID.avd; } catch { return false; }
  });
}

function androidUp({ headless }) {
  if (!avdExists()) createAvd();
  let serial = emulatorSerial();
  if (!serial) {
    console.log(`Android: starting ${ANDROID.avd}…`);
    run(ANDROID_CLI, ["emulator", "start", ...(headless ? ["--headless"] : []), ANDROID.avd], { stdio: ["ignore", "ignore", "inherit"] });
    writeState({ androidBooted: true });
    serial = emulatorSerial();
    if (!serial) throw new Error(`emulator ${ANDROID.avd} started but adb can't see it`);
  }
  adb(serial, "shell", "settings", "put", "global", "sysui_demo_allowed", "1");
  for (const c of [["enter"], ["clock", "-e", "hhmm", "0941"], ["battery", "-e", "level", "100", "-e", "plugged", "false"],
    ["network", "-e", "wifi", "show", "-e", "level", "4", "-e", "fully", "true"],
    ["network", "-e", "mobile", "show", "-e", "datatype", "none", "-e", "level", "4", "-e", "fully", "true"],
    ["notifications", "-e", "visible", "false"]]) {
    adb(serial, "shell", "am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", ...c);
  }
  adb(serial, "shell", "cmd", "uimode", "night", "no");
  console.log(`Android: ${ANDROID.avd} (${serial})`);
  return serial;
}

function platforms(only) {
  return only ? [only] : ["ios", "android"];
}

function devices(only) {
  const d = {};
  if (platforms(only).includes("ios")) {
    const sim = findSim();
    if (!sim || sim.state !== "Booted") throw new Error(`simulator "${IOS.name}" isn't running: run \`up\` first`);
    d.ios = sim.udid;
  }
  if (platforms(only).includes("android")) {
    const serial = emulatorSerial();
    if (!serial) throw new Error(`emulator ${ANDROID.avd} isn't running: run \`up\` first`);
    d.android = serial;
  }
  return d;
}

function up(opts) {
  if (platforms(opts.only).includes("ios")) iosUp(opts);
  if (platforms(opts.only).includes("android")) androidUp(opts);
}

function down() {
  const state = readState();
  const sim = findSim();
  if (sim && sim.state === "Booted") {
    try { simctl("status_bar", sim.udid, "clear"); } catch {}
    if (state.iosBooted) { simctl("shutdown", sim.udid); console.log(`shut down ${IOS.name}`); }
  }
  const serial = emulatorSerial();
  if (serial) {
    try { adb(serial, "shell", "am", "broadcast", "-a", "com.android.systemui.demo", "-e", "command", "exit"); } catch {}
    if (state.androidBooted) { adb(serial, "emu", "kill"); console.log(`shut down ${ANDROID.avd}`); }
  }
  fs.rmSync(STATE, { force: true });
}

// ---- build / install -------------------------------------------------------

function buildStep(name, cmd, args, cwd) {
  const log = path.join(CACHE, `${name}-build.log`);
  const r = spawnSync(cmd, args, { cwd, encoding: "utf8", maxBuffer: 1 << 28 });
  fs.appendFileSync(log, (r.stdout || "") + (r.stderr || ""));
  if (r.status !== 0) {
    const tail = fs.readFileSync(log, "utf8").split("\n").filter((l) => /error|FAILED|What went wrong/i.test(l)).slice(-15).join("\n");
    throw new Error(`${name} build failed (full log: ${log})\n${tail}`);
  }
}

function build(which) {
  for (const p of ["ios", "android"]) fs.rmSync(path.join(CACHE, `${p}-build.log`), { force: true });
  if (which !== "android") {
    console.log("building ios…");
    const ios = path.join(MOBILE, "ios");
    buildStep("ios", "xcodegen", ["generate", "--quiet"], ios);
    buildStep("ios", "xcodebuild", ["-project", "LpmMobile.xcodeproj", "-scheme", "LpmMobile", "-configuration", "Debug",
      "-sdk", "iphonesimulator", "-destination", "generic/platform=iOS Simulator",
      "-derivedDataPath", path.join(CACHE, "ios-dd"), ...IOS_SIGNING, "build"], ios);
  }
  if (which !== "ios") {
    console.log("building android…");
    buildStep("android", "./gradlew", ["assembleDebug", "-q"], path.join(MOBILE, "android"));
  }
}

function install(only) {
  const d = devices(only);
  if (d.ios) simctl("install", d.ios, IOS_APP);
  if (d.android) adb(d.android, "install", "-r", APK);
  console.log(`installed on ${Object.keys(d).join(" + ")}`);
}

// ---- test / report ---------------------------------------------------------

function maestro(platform, device, flows, out) {
  fs.mkdirSync(out, { recursive: true });
  const log = fs.openSync(path.join(out, "maestro.log"), "w");
  return new Promise((resolve) => {
    spawn("maestro", ["--device", device, "test", "--format", "junit", "--output", path.join(out, "report.xml"),
      "--test-output-dir", out, ...flows], { cwd: FLOWS, stdio: ["ignore", log, log] })
      .on("exit", (code) => resolve(code));
  });
}

function results(dir) {
  const xml = fs.existsSync(path.join(dir, "report.xml")) ? fs.readFileSync(path.join(dir, "report.xml"), "utf8") : "";
  const out = {};
  for (const m of xml.matchAll(/<testcase\b([^>]*?)(\/>|>([\s\S]*?)<\/testcase>)/g)) {
    const name = /\bname="([^"]*)"/.exec(m[1])?.[1];
    const failure = /<failure[^>]*?(?:message="([^"]*)")?[^>]*>([\s\S]*?)<\/failure>|<failure[^>]*message="([^"]*)"/.exec(m[3] || "");
    if (name) out[name] = failure ? (failure[1] || failure[3] || failure[2] || "failed").trim() : null;
  }
  return out;
}

function screenshots(dir) {
  const shots = {};
  const walk = (d) => {
    for (const e of fs.existsSync(d) ? fs.readdirSync(d, { withFileTypes: true }) : []) {
      const p = path.join(d, e.name);
      if (e.isDirectory()) walk(p);
      else if (e.name.endsWith(".png")) {
        // <out>/<timestamp>/<flow>/takeScreenshot/<step>.png, or a failure shot in <timestamp>/
        const parts = path.relative(dir, p).split(path.sep);
        const flow = parts.length >= 4 ? parts[1] : "failures";
        (shots[flow] ||= []).push({ step: path.basename(e.name, ".png"), file: path.relative(path.dirname(dir), p), at: fs.statSync(p).mtimeMs });
      }
    }
  };
  walk(dir);
  return shots;
}

const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);

function report(runDir) {
  const platforms = ["ios", "android"];
  const res = Object.fromEntries(platforms.map((p) => [p, results(path.join(runDir, p))]));
  const shots = Object.fromEntries(platforms.map((p) => [p, screenshots(path.join(runDir, p))]));
  const flows = [...new Set(platforms.flatMap((p) => [...Object.keys(res[p]), ...Object.keys(shots[p])]))].sort();
  const mark = (p, f) => (f in res[p] ? (res[p][f] ? "✗" : "✓") : "–");
  let rows = "", sections = "";
  for (const f of flows) {
    rows += `<tr><td><a href="#${esc(f)}">${esc(f)}</a></td>${platforms.map((p) => `<td class="${mark(p, f) === "✓" ? "ok" : "bad"}">${mark(p, f)}</td>`).join("")}</tr>`;
    const steps = [];
    for (const p of platforms) for (const s of (shots[p][f] || []).sort((a, b) => a.at - b.at)) if (!steps.includes(s.step)) steps.push(s.step);
    const cell = (p, step) => {
      const s = (shots[p][f] || []).find((x) => x.step === step);
      return s ? `<img src="${esc(s.file)}" loading="lazy">` : `<div class="missing">not reached</div>`;
    };
    const errors = platforms.filter((p) => res[p][f]).map((p) => `<p class="err">${p}: ${esc(res[p][f])}</p>`).join("");
    sections += `<section id="${esc(f)}"><h2>${esc(f)}</h2>${errors}` +
      steps.map((st) => `<h3>${esc(st)}</h3><div class="pair">${platforms.map((p) => cell(p, st)).join("")}</div>`).join("") + "</section>";
  }
  const html = `<!doctype html><meta charset="utf-8"><title>lpm Link parity</title>
<style>body{font:14px -apple-system,system-ui,sans-serif;margin:24px;background:#fff;color:#111}
table{border-collapse:collapse}td,th{border:1px solid #ddd;padding:4px 10px}.ok{color:#1a7f37}.bad{color:#cf222e}
.pair{display:grid;grid-template-columns:1fr 1fr;gap:16px;max-width:960px}.pair img{width:100%;border:1px solid #ddd;border-radius:12px}
.missing{display:flex;align-items:center;justify-content:center;border:1px dashed #bbb;border-radius:12px;min-height:200px;color:#888}
.err{color:#cf222e;white-space:pre-wrap}.cols{display:grid;grid-template-columns:1fr 1fr;gap:16px;max-width:960px;font-weight:600}</style>
<h1>lpm Link parity — ${esc(path.basename(runDir))}</h1>
<table><tr><th>Flow</th><th>iOS</th><th>Android</th></tr>${rows}</table>
<div class="cols"><div>iOS (reference)</div><div>Android</div></div>${sections}`;
  const file = path.join(runDir, "report.html");
  fs.writeFileSync(file, html);
  return { file, res, flows };
}

async function test(args, opts) {
  const d = devices(opts.only);
  const flows = args.length ? args.map((a) => path.resolve(FLOWS, a.endsWith(".yaml") ? a : `${a}.yaml`)) : [FLOWS];
  const stamp = new Date().toISOString().replace(/[:T]/g, "-").slice(0, 19);
  const runDir = path.join(CACHE, "runs", stamp);
  const targets = platforms(opts.only);
  console.log(`running ${args.length ? args.join(", ") : "all flows"} on ${targets.join(" + ")}…`);
  await Promise.all(targets.map((p) => maestro(p, d[p], flows, path.join(runDir, p))));
  const latest = path.join(CACHE, "runs", "latest");
  try { fs.unlinkSync(latest); } catch {}
  fs.symlinkSync(runDir, latest);
  const { file, res, flows: names } = report(runDir);
  for (const f of names) console.log(`${f.padEnd(28)} iOS ${f in res.ios ? (res.ios[f] ? "✗" : "✓") : "–"}   Android ${f in res.android ? (res.android[f] ? "✗" : "✓") : "–"}`);
  console.log(`report: ${file}`);
  if (opts.open) spawnSync("open", [file]);
  if (Object.values(res.ios).some(Boolean)) process.exitCode = 1;
}

// ---- cli -------------------------------------------------------------------

const USAGE = `usage: node mobile/parity/parity.js <command>
  doctor                     check every tool both apps and the harness need
  up [--headless]            boot (or create) the iPhone simulator and the Android emulator
  build [ios|android]        Debug builds of both apps (iOS into ${CACHE}/ios-dd)
  install                    install both builds on the running devices
                             (up, install, test and all take --only ios|android)
  test [flow…] [--only ios|android] [--open]
                             run flows (default: all in flows/) on both devices at once,
                             then write a side-by-side report
  report [run-dir]           rebuild the report for a run (default: the latest)
  all [--headless] [--open]  up, build, install, test
  down                       clear status bars, shut down devices this harness booted`;

async function main() {
  const [cmd, ...rest] = process.argv.slice(2);
  const flags = new Set(rest.filter((a) => a.startsWith("--")));
  const args = rest.filter((a, i) => !a.startsWith("--") && rest[i - 1] !== "--only");
  const only = rest.includes("--only") ? rest[rest.indexOf("--only") + 1] : null;
  const opts = { headless: flags.has("--headless"), open: flags.has("--open"), only };
  switch (cmd) {
    case "doctor": return doctor();
    case "up": return up(opts);
    case "build": return build(args[0]);
    case "install": return install(only);
    case "test": return test(args, opts);
    case "report": return console.log(report(args[0] || fs.realpathSync(path.join(CACHE, "runs", "latest"))).file);
    case "all": up(opts); build(only); install(only); return test([], opts);
    case "down": return down();
    default: console.log(USAGE); if (cmd) process.exitCode = 1;
  }
}

main().catch((e) => { console.error(e.message); process.exit(1); });
