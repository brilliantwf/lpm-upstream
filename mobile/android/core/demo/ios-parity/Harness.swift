import Foundation

// Virtual time: demoNow() and demoAfter(...) replace Date() and main-queue timers, with
// delays rounded up to whole milliseconds the way kotlinx.coroutines' delay(Duration) does.
var demoVirtualMs: Int64 = 0
let demoBaseMs = Int64(ProcessInfo.processInfo.environment["DEMO_BASE_MS"]!)!
func demoNow() -> Date { Date(timeIntervalSince1970: Double(demoBaseMs + demoVirtualMs) / 1000) }

struct Pending { let at: Int64; let seq: Int; let block: () -> Void }
var pending: [Pending] = []
var pendingSeq = 0
func delayMs(_ s: Double) -> Int64 {
    s > 0 ? (Int64((s * 1e9).rounded()) + 999_999) / 1_000_000 : 0
}
func demoAfter(_ seconds: Double, _ block: @escaping () -> Void) {
    pendingSeq += 1
    pending.append(Pending(at: demoVirtualMs + delayMs(seconds), seq: pendingSeq, block: block))
}
func runUntil(_ t: Int64) {
    while let next = pending.min(by: { ($0.at, $0.seq) < ($1.at, $1.seq) }), next.at <= t {
        pending.removeAll { $0.seq == next.seq }
        demoVirtualMs = next.at
        next.block()
    }
    demoVirtualMs = t
}

let server = DemoServer()
server.deliver = { text in
    print("{\"at\":\(demoVirtualMs),\"frame\":\(text)}")
}
let script = try! String(contentsOfFile: CommandLine.arguments[1], encoding: .utf8)
var end: Int64 = 0
for line in script.split(separator: "\n") where !line.trimmingCharacters(in: .whitespaces).isEmpty {
    let step = try! JSONSerialization.jsonObject(with: Data(line.utf8)) as! [String: Any]
    let at = (step["at"] as! NSNumber).int64Value
    if let frame = step["frame"] as? [String: Any] {
        runUntil(at)
        server.receive(String(data: try! JSONSerialization.data(withJSONObject: frame), encoding: .utf8)!)
    } else if let raw = step["raw"] as? String {
        runUntil(at)
        server.receive(raw)
    } else {
        end = at
    }
}
runUntil(end)
