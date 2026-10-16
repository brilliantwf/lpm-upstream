package cx.lpm.mobile.core.demo

/**
 * The simulated Mac's Claude accounts: the main login, two personal plans (one not yet
 * confirmed for taking turns), a team seat that isn't allowed, and one signed out, with
 * switching on.
 */
internal class ClaudeDemo {
    data class Account(
        val id: String,
        val label: String,
        val signedIn: Boolean,
        val email: String,
        val plan: String
    )

    val accounts = listOf(
        Account(
            id = CLAUDE_MAIN_LOGIN,
            label = "",
            signedIn = true,
            email = "you@example.com",
            plan = "max"
        ),
        Account(
            id = "work",
            label = "Work",
            signedIn = true,
            email = "you@work.example",
            plan = "max"
        ),
        Account(
            id = "personal",
            label = "Personal",
            signedIn = true,
            email = "me@example.com",
            plan = "pro"
        ),
        Account(
            id = "client",
            label = "Client",
            signedIn = true,
            email = "dev@client.example",
            plan = "team"
        ),
        Account(id = "laptop", label = "Old laptop", signedIn = false, email = "", plan = "")
    )
    val confirmed = mutableSetOf(CLAUDE_MAIN_LOGIN, "work")
}

/**
 * Demo handlers for the Claude Account menu: the accounts and switching state, and
 * setting a project's choice as the Mac would write it.
 */
internal fun DemoServer.registerClaudeAccountHandlers() {
    register("claudeAccounts") {
        pushAfter(0.2) { claudeAccountsPayload() }
    }
    register("setClaudeAccount") { o ->
        val project = o.string("project") ?: return@register
        val choice = o.dict("choice")?.let(ClaudeAccountChoice::fromWire)
        val p = world.projectIndex(project)?.let { world.projects[it] }
        if (choice == null || p == null) {
            push(
                mapOf(
                    "t" to "setClaudeAccount",
                    "project" to project,
                    "ok" to false,
                    "error" to "That project isn't on this Mac."
                )
            )
            return@register
        }
        world.claude.confirmed += o.strings("confirm") ?: emptyList()
        val own = choice.own(isCopy = p.parentName.isNotEmpty())
        p.claudeAccount = own.account
        p.claudeAccounts = own.accounts
        push(mapOf("t" to "setClaudeAccount", "project" to project, "ok" to true))
        pushAfter(0.1) { mapOf("t" to "projects-changed") }
    }
}

/**
 * Each list takes turns starting on its first signed-in account; the main accounts take
 * turns between the main login and Work, on the main login now.
 */
private fun DemoServer.claudeAccountsPayload(): Dict {
    val claude = world.claude
    val signedIn = claude.accounts.filter { it.signedIn }.map { it.id }.toSet()
    val pools = mutableListOf<Dict>(
        mapOf(
            "project" to null,
            "members" to listOf(CLAUDE_MAIN_LOGIN, "work"),
            "current" to CLAUDE_MAIN_LOGIN
        )
    )
    for (p in world.projects) {
        if (p.claudeAccount != null) continue
        val list = p.claudeAccounts ?: continue
        val first = list.firstOrNull { it in signedIn } ?: continue
        pools.add(mapOf("project" to p.name, "members" to list, "current" to first))
    }
    return mapOf(
        "t" to "claudeAccounts",
        "ok" to true,
        "accounts" to claude.accounts.map { a ->
            mapOf(
                "id" to a.id,
                "label" to a.label,
                "signedIn" to a.signedIn,
                "email" to a.email,
                "plan" to a.plan,
                "confirmed" to (a.id in claude.confirmed)
            )
        },
        "switching" to mapOf(
            "enabled" to true,
            "active" to true,
            "paused" to null,
            "allowed" to emptyList<String>(),
            "maxMembers" to 3,
            "pools" to pools
        )
    )
}
