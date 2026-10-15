import SwiftUI

/// One line of the usage rows under the projects list and a project's screen —
/// the port of the desktop sidebar's `usageRows` (sidebarUsage.ts): one line per
/// agent CLI, or per Claude account once more than one is in play, with when its
/// window comes back over a bar of how much of it is gone.
struct UsageRow: Identifiable {
    /// The account for a Claude row ("claude:<id>"), the tool otherwise.
    let id: String
    let provider: String
    let label: String
    /// The account's signed-in email, for the expanded card.
    let subtitle: String?
    let color: Color
    /// The limit palette when a window is known, the tool's own color when the
    /// row only reports what it spent today.
    let fill: Color
    let fraction: Double
    /// How far through its window the clock is (0…1), where the pace tick sits.
    let pace: Double?
    /// When the window resets, today's spend without a window, or a dash.
    let detail: String
    let percentText: String
    let windowLabel: String
    let tokens: Int
    let sessions: Int
    let data: ProviderLimits?
    let stale: Bool

    /// The Claude account this row is about, as its limits key; nil for a tool row.
    var account: String? {
        id.hasPrefix("claude:") ? String(id.dropFirst("claude:".count)) : nil
    }

    /// "1.2M spent today · 3 sessions", or nil when nothing was spent.
    var spentLine: String? {
        guard tokens > 0 else { return nil }
        let count = sessions == 1 ? "1 session" : "\(sessions) sessions"
        return "\(formatTokenCount(tokens)) spent today · \(count)"
    }
}

private let usageTools = ["claude", "codex"]

private struct UsageIdentity {
    let id: String
    let name: String
    var subtitle: String?
    var data: ProviderLimits?
}

/// One row per agent CLI — or per Claude account, once more than one is in play —
/// in a fixed order so rows never swap places under a finger. A tool appears once
/// it has a usage window or has spent something today.
///
/// `now` is the phone's clock, against which the tools' own `resetsAt` stamps are
/// judged; `macNow` is the Mac's, for the `updatedAt` it stamped.
func usageRows(_ snapshot: LimitsSnapshot?, today: AgentStats?, accounts: [ClaudeAccountInfo],
               now: Double, macNow: Double) -> [UsageRow] {
    let entries = snapshot.map { Array($0.entries.values) } ?? []

    struct Candidate {
        let provider: String
        let identity: UsageIdentity
        let split: Bool
        let win: LimitWindow?
        let windowLabel: String
        let windowMs: Double
        let tokens: Int
        let sessions: Int
    }

    var candidates: [Candidate] = []
    for provider in usageTools {
        let identities = identitiesFor(provider, entries: entries, accounts: accounts)
        let split = identities.count > 1
        // The day's spend is counted per tool, not per account, so it only stands
        // in for a tool that shows a single row.
        let spent: UsageBreakdown? = split ? nil : today?.providers.first { $0.key == provider }
        for identity in identities {
            let picked = pickWindow(identity.data, now: now)
            let candidate = Candidate(provider: provider, identity: identity, split: split,
                                      win: picked.win, windowLabel: picked.label, windowMs: picked.windowMs,
                                      tokens: spent?.tokens.totalTokens ?? 0, sessions: spent?.sessions ?? 0)
            // An account row stays even with nothing to show — a row that vanished
            // when an account went idle would read as the account being gone.
            if candidate.win != nil || candidate.tokens > 0 || split { candidates.append(candidate) }
        }
    }

    // Rows without a window are measured against each other: no plan publishes a
    // token budget to measure them against.
    let peak = candidates.filter { $0.win == nil }.map(\.tokens).max() ?? 0

    return candidates.map { c in
        let meta = providerMeta(c.provider)
        let used = c.win.map { $0.usedPercent.isFinite ? $0.usedPercent : 0 }
        let fraction: Double
        if let used {
            fraction = max(0, min(100, used)) / 100
        } else {
            fraction = peak > 0 ? Double(c.tokens) / Double(peak) : 0
        }
        let detail: String
        if let win = c.win {
            detail = resetShort(win.resetsAt, now: now)
        } else {
            detail = c.tokens > 0 ? "\(formatTokenCount(c.tokens)) today" : "—"
        }
        let pace = computePace(c.win, windowMs: c.windowMs, now: now).map { $0.elapsedPercent / 100 }
        let data = c.identity.data
        return UsageRow(
            id: c.identity.id,
            provider: c.provider,
            label: c.split ? c.identity.name : meta.short,
            subtitle: c.split ? c.identity.subtitle : nil,
            color: meta.color,
            fill: used.map { barTint(max(0, min(100, $0))) } ?? meta.color,
            fraction: fraction,
            pace: c.win == nil ? nil : pace,
            detail: detail,
            percentText: used.map { "\(Int($0.rounded()))%" } ?? "",
            windowLabel: c.win == nil ? "" : c.windowLabel,
            tokens: c.tokens,
            sessions: c.sessions,
            data: data,
            stale: data.map { macNow - Double($0.updatedAt) > staleMs } ?? false
        )
    }
}

private func identitiesFor(_ provider: String, entries: [ProviderLimits],
                           accounts: [ClaudeAccountInfo]) -> [UsageIdentity] {
    if provider == "claude" { return claudeIdentities(entries, accounts: accounts) }
    let latest = entries.filter { $0.provider == provider }.max { $0.updatedAt < $1.updatedAt }
    return [UsageIdentity(id: provider, name: providerMeta(provider).short, data: latest)]
}

/// One identity per Claude account the person has: every account that reported,
/// plus the ones added on the Mac that have not run yet. Two accounts mean two
/// rows — they burn down separately.
private func claudeIdentities(_ entries: [ProviderLimits], accounts: [ClaudeAccountInfo]) -> [UsageIdentity] {
    let registered = accounts.filter { $0.id != claudeMainLogin }
    let known = Set(registered.map(\.id))
    var reported: [String: ProviderLimits] = [:]
    for entry in entries where entry.provider == "claude" {
        let id = entry.accountId.flatMap { $0.isEmpty ? nil : $0 } ?? claudeMainLogin
        // An account no longer on the Mac can never report again; its last
        // reading would linger as a row named by a raw id.
        guard id == claudeMainLogin || known.contains(id) else { continue }
        if let prev = reported[id], prev.updatedAt >= entry.updatedAt { continue }
        reported[id] = entry
    }
    if reported.isEmpty {
        return [UsageIdentity(id: "claude", name: providerMeta("claude").short)]
    }
    var ids = registered.map(\.id)
    if reported[claudeMainLogin] != nil { ids.append(claudeMainLogin) }
    return ids.map { id in
        let account = registered.first { $0.id == id }
        return UsageIdentity(
            id: "claude:\(id)",
            name: id == claudeMainLogin ? providerMeta("claude").short : account?.name ?? id,
            subtitle: account.flatMap { $0.email.isEmpty ? nil : $0.email },
            data: reported[id]
        )
    }
}

/// The weekly window — the one that actually runs out on people, and the
/// desktop sidebar's default — falling back to the 5-hour one. A window past its
/// reset describes one that no longer exists and is left out.
private func pickWindow(_ data: ProviderLimits?, now: Double) -> (win: LimitWindow?, label: String, windowMs: Double) {
    guard let data else { return (nil, "", 0) }
    if let weekly = liveWindow(data.weekly, now: now) { return (weekly, "weekly", weeklyMs) }
    return (liveWindow(data.fiveHour, now: now), "5-hour", fiveHourMs)
}

private func liveWindow(_ win: LimitWindow?, now: Double) -> LimitWindow? {
    guard let win else { return nil }
    return win.resetsAt > 0 && Double(win.resetsAt) * 1000 <= now ? nil : win
}
