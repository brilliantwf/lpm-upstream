import SwiftUI

/// A list the Claude Account menu wants to save, held until the person confirms
/// that the accounts joining it are theirs alone.
struct ClaudeConsentRequest: Identifiable {
    let id = UUID()
    let project: Project
    let ids: [String]
    let accounts: [ClaudeAccountInfo]
}

private struct ClaudeConsentPrompt: ViewModifier {
    @Environment(AppModel.self) private var model
    @Binding var request: ClaudeConsentRequest?

    func body(content: Content) -> some View {
        content.alert(
            (request?.accounts.count ?? 1) == 1 ? "Add this account to the list?" : "Add these accounts to the list?",
            isPresented: Binding(
                get: { request != nil },
                set: { if !$0 { request = nil } }
            ),
            presenting: request
        ) { r in
            Button(r.accounts.count == 1 ? "It's Only Mine" : "They're Only Mine") {
                model.setClaudeAccount(r.project, .list(r.ids), confirm: r.accounts.map(\.id))
            }
            Button("Cancel", role: .cancel) {}
        } message: { r in
            Text(message(r))
        }
    }

    /// The desktop's notice, word for word, after the accounts it asks about.
    private func message(_ r: ClaudeConsentRequest) -> String {
        let names = r.accounts
            .map { $0.email.isEmpty ? $0.label : "\($0.label) (\($0.email))" }
            .joined(separator: ", ")
        return """
        \(names)

        New Claude sessions start on the first account in your list and move to the next one only when an account is close to its 5-hour or weekly limit. Sessions already running stay on their account.

        Every account must be yours alone. Anthropic hasn’t said whether switching accounts when one runs low is allowed, and it can limit or close accounts it believes break its terms. Usage credits or a higher plan are Anthropic’s supported ways to keep working past a limit.
        """
    }
}

extension View {
    func claudeConsentPrompt(_ request: Binding<ClaudeConsentRequest?>) -> some View {
        modifier(ClaudeConsentPrompt(request: request))
    }
}
