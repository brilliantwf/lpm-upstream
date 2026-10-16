package cx.lpm.mobile.core.demo

import kotlin.random.Random

// The fake shell behind every non-agent terminal: line editing, a handful of commands,
// and the dev server's log lines ticking in while the project runs.

internal fun DemoServer.handleShellInput(id: String, d: String) {
    val term = demoTerminal(id) ?: return
    val prompt = shellPrompt(term.project)
    var pending = term.pendingInput
    var echo = ""
    val chars = d.graphemes()
    var i = 0
    while (i < chars.size) {
        when (val ch = chars[i]) {
            "\u001B" -> {
                i += skipEscape(chars, i)
                continue
            }

            "\r", "\n" -> {
                echo += "\r\n"
                val cmd = pending
                pending = ""
                updateTerminal(id) { it.pendingInput = "" }
                emit(id, echo)
                echo = ""
                runShellCommand(id, cmd)
            }

            "\u007F", "\b" -> if (pending.isNotEmpty()) {
                pending = pending.dropLastGrapheme()
                echo += "\b \b"
            }

            "\u0003" -> {
                pending = ""
                echo += "^C\r\n" + prompt
            }

            else -> if (ch.codePointAt(0) >= 0x20) {
                pending += ch
                echo += ch
            }
        }
        i += 1
    }
    updateTerminal(id) { it.pendingInput = pending }
    if (echo.isNotEmpty()) emit(id, echo)
}

private fun DemoServer.runShellCommand(id: String, raw: String) {
    val term = demoTerminal(id) ?: return
    val project = term.project
    val prompt = shellPrompt(project)
    val cmd = raw.trimmingWhitespaces()
    val word = cmd.swiftSplit(' ').firstOrNull() ?: ""
    when (cmd) {
        "" -> emit(id, prompt)

        "ls", "ls -a", "ls -la", "ll" -> emit(id, lsOutput(project) + prompt)

        "pwd" -> emit(id, "/Users/demo/dev/$project\r\n" + prompt)

        "whoami" -> emit(id, "demo\r\n" + prompt)

        "clear" -> {
            setBuffer(id, prompt)
            push(mapOf("t" to "o", "id" to id, "d" to "\u001B[2J\u001B[3J\u001B[H" + prompt))
        }

        "git status" -> emit(id, gitStatusOutput(project) + prompt)

        "git log" -> emit(id, gitLogOutput(project) + prompt)

        "npm test", "npm t", "npm run test" -> {
            emit(id, "\r\n> $project@0.4.2 test\r\n> vitest run\r\n\r\n")
            emitAfter(1.3, id, testResults + prompt)
        }

        "npm run dev" -> {
            emit(id, "\r\n> $project@0.4.2 dev\r\n> next dev\r\n\r\n")
            emitAfter(0.9, id, " $RED⨯$RESET Port 3000 is already in use\r\n" + prompt)
        }

        "npm run lint" -> {
            emit(id, "\r\n> $project@0.4.2 lint\r\n> next lint\r\n\r\n")
            emitAfter(1.1, id, "$GREEN✔$RESET No ESLint warnings or errors\r\n" + prompt)
        }

        else -> when (word) {
            "claude" -> emit(
                id,
                "$ORANGE✻$RESET Claude Code is already running in the ${BOLD}Claude$RESET " +
                    "tab of this project.\r\n" + prompt
            )

            "echo" -> emit(id, cmd.drop(5) + "\r\n" + prompt)

            "git" -> {
                val sub = cmd.swiftSplit(' ').drop(1).firstOrNull() ?: ""
                emit(id, "git: '$sub' is not a git command. See 'git --help'.\r\n" + prompt)
            }

            else -> emit(id, "zsh: command not found: $word\r\n" + prompt)
        }
    }
}

private val testResults =
    " $GREEN✓$RESET lib/cart.test.ts $DIM(8 tests) 42ms$RESET\r\n" +
        " $GREEN✓$RESET lib/payments.test.ts $DIM(6 tests) 38ms$RESET\r\n" +
        " $GREEN✓$RESET components/CheckoutForm.test.tsx $DIM(9 tests) 87ms$RESET\r\n\r\n" +
        " ${DIM}Test Files$RESET  ${GREEN}3 passed$RESET (3)\r\n" +
        " $DIM     Tests$RESET  ${GREEN}23 passed$RESET (23)\r\n" +
        " $DIM  Duration$RESET  1.24s\r\n\r\n"

private fun DemoServer.gitStatusOutput(project: String): String {
    val r = world.git[project]
    if (r == null || !r.isRepo) {
        return "fatal: not a git repository (or any of the parent directories): .git\r\n"
    }
    var out = "On branch ${r.branch}\r\n"
    if (r.ahead > 0 && r.hasUpstream) {
        val plural = if (r.ahead == 1) "" else "s"
        out += "Your branch is ahead of 'origin/${r.branch}' by ${r.ahead} commit$plural.\r\n"
        out += "  (use \"git push\" to publish your local commits)\r\n"
    }
    val staged = r.files.filter { it.staged || it.status == "added" }
    val unstaged = r.files.filter {
        !it.staged && it.status in listOf("modified", "deleted", "renamed")
    }
    val untracked = r.files.filter { !it.staged && it.status == "untracked" }
    if (staged.isEmpty() && unstaged.isEmpty() && untracked.isEmpty()) {
        return out + "\r\nnothing to commit, working tree clean\r\n"
    }
    fun label(s: String) = when (s) {
        "added" -> "new file:   "
        "deleted" -> "deleted:    "
        "renamed" -> "renamed:    "
        else -> "modified:   "
    }
    if (staged.isNotEmpty()) {
        out += "\r\nChanges to be committed:\r\n" +
            "  (use \"git restore --staged <file>...\" to unstage)\r\n"
        for (f in staged) out += "\t$GREEN${label(f.status)}${f.path}$RESET\r\n"
    }
    if (unstaged.isNotEmpty()) {
        out += "\r\nChanges not staged for commit:\r\n" +
            "  (use \"git add <file>...\" to update what will be committed)\r\n"
        for (f in unstaged) out += "\t$RED${label(f.status)}${f.path}$RESET\r\n"
    }
    if (untracked.isNotEmpty()) {
        out += "\r\nUntracked files:\r\n" +
            "  (use \"git add <file>...\" to include in what will be committed)\r\n"
        for (f in untracked) out += "\t$RED${f.path}$RESET\r\n"
    }
    return out
}

private fun DemoServer.gitLogOutput(project: String): String {
    val branch = world.git[project]?.branch ?: "main"
    return "${YELLOW}4f2a9c1$RESET (${CYAN}HEAD -> $RESET$GREEN$branch$RESET) " +
        "checkout: extract payment helpers\r\n" +
        "${YELLOW}8b31d02$RESET checkout: scaffold two-step form\r\n" +
        "${YELLOW}2c9e7aa$RESET (${RED}origin/main$RESET, ${GREEN}main$RESET) " +
        "cart: fix badge count on hydration\r\n"
}

internal fun DemoServer.armDevLog(id: String) {
    val t = demoTerminal(id) ?: return
    if (t.cli.isNotEmpty() || t.logTickArmed) return
    updateTerminal(id) { it.logTickArmed = true }
    scheduleDevLogTick(id)
}

private fun DemoServer.scheduleDevLogTick(id: String) {
    pushAfter(Random.nextDouble(4.0, 8.0)) {
        val t = demoTerminal(id) ?: return@pushAfter null
        if (!t.subscribed) {
            updateTerminal(id) { it.logTickArmed = false }
            return@pushAfter null
        }
        scheduleDevLogTick(id)
        if (world.project(t.project)?.running != true) return@pushAfter null
        val chunk = "\r\u001B[2K" + devLogLines.random() + "\r\n" +
            shellPrompt(t.project) + t.pendingInput
        appendBuffer(id, chunk)
        mapOf("t" to "o", "id" to id, "d" to chunk)
    }
}
