package cx.lpm.mobile.core.demo

/** The last Auto Commit / Auto Commit and Push / Auto Create PR run on a project. */
internal data class GitAutoRun(
    val runId: Int,
    val nonce: String,
    val kind: String,
    var phase: String = "running",
    var canceling: Boolean = false,
    val steps: List<Step>,
    var error: String = "",
    var message: String = "",
    var url: String = "",
    var title: String = "",
    var branch: String = "",
    var base: String = ""
) {
    data class Step(val id: String, var status: String = "pending", var detail: String = "")

    val payload: Dict
        get() {
            val d = mutableMapOf<String, Any?>(
                "runId" to runId,
                "nonce" to nonce,
                "kind" to kind,
                "phase" to phase,
                "canceling" to canceling,
                "rebased" to false,
                "steps" to steps.map { s ->
                    val step = mutableMapOf<String, Any?>("id" to s.id, "status" to s.status)
                    if (s.detail.isNotEmpty()) step["detail"] = s.detail
                    step
                }
            )
            val texts = listOf(
                "error" to error,
                "message" to message,
                "url" to url,
                "title" to title,
                "branch" to branch,
                "base" to base
            )
            for ((k, v) in texts) if (v.isNotEmpty()) d[k] = v
            return d
        }

    fun set(id: String, status: String, detail: String = "") {
        val step = steps.firstOrNull { it.id == id } ?: return
        step.status = status
        step.detail = detail
    }
}

/**
 * Demo handlers for Auto Commit / Auto Commit and Push / Auto Create PR: the Mac's
 * step-by-step run, played out on timers against the demo repo, with Stop honored
 * between beats the way the Mac honors it between steps.
 */
internal fun DemoServer.registerGitAutoHandlers() {
    register("gitAuto") { o ->
        val project = o.string("project") ?: return@register
        demoStartGitAuto(project, kind = o.string("kind") ?: "", nonce = o.string("nonce") ?: "")
    }
    register("gitAutoState") { o ->
        val project = o.string("project") ?: return@register
        push(gitAutoFrame(project))
    }
    register("gitAutoCancel") { o ->
        val project = o.string("project") ?: return@register
        val run = world.gitAutoRuns[project]?.takeIf { it.phase == "running" } ?: return@register
        run.canceling = true
        push(gitAutoFrame(project))
    }
    register("gitAutoSwitchBase") { o ->
        val project = o.string("project") ?: return@register
        val base = o.string("base") ?: return@register
        pushAfter(0.8) {
            val repo = world.git[project] ?: return@pushAfter null
            repo.aheadByBranch += repo.branch to repo.ahead
            repo.branch = base
            repo.ahead = repo.aheadByBranch[base] ?: 0
            repo.behind = 0
            repo.hasUpstream = true
            pushGitChanged(project)
            mapOf("t" to "gitAutoSwitchBase", "project" to project, "ok" to true, "base" to base)
        }
    }
}

private fun DemoServer.gitAutoFrame(project: String): Dict = mapOf(
    "t" to "gitAuto",
    "project" to project,
    "ok" to true,
    "run" to world.gitAutoRuns[project]?.payload
)

private fun DemoServer.demoStartGitAuto(project: String, kind: String, nonce: String) {
    if (world.gitAutoRuns[project]?.phase == "running") {
        push(gitAutoFrame(project))
        return
    }
    // A snapshot: the beats read the repo as it was when the run started.
    val repo = world.git[project]?.copy()
    if (repo == null) {
        push(
            mapOf(
                "t" to "gitAuto",
                "project" to project,
                "ok" to false,
                "error" to "Not a git repository."
            )
        )
        return
    }
    val ids = when (kind) {
        "commit" -> listOf("commit")

        "commitPush" -> listOf("commit", "push")

        "pr" -> listOf("branch", "commit", "push", "pr")

        else -> {
            push(
                mapOf(
                    "t" to "gitAuto",
                    "project" to project,
                    "ok" to false,
                    "error" to "Unknown action."
                )
            )
            return
        }
    }
    val runId = world.gitAutoNextId
    world.gitAutoNextId += 1
    world.gitAutoRuns[project] = GitAutoRun(
        runId = runId,
        nonce = nonce,
        kind = kind,
        steps = ids.map { GitAutoRun.Step(id = it) }
    )
    push(gitAutoFrame(project))

    val beats = when (kind) {
        "pr" -> prBeats(project, repo)
        else -> commitBeats(project, andPush = kind == "commitPush")
    }
    playGitAuto(project, runId, beats)
}

private class Beat(val delay: Double, val apply: (GitAutoRun) -> Unit)

/**
 * Apply each beat after its delay, pushing the new state, until the run is stopped or
 * out of beats; a run that got through every beat is done.
 */
private fun DemoServer.playGitAuto(project: String, runId: Int, beats: List<Beat>) {
    val beat = beats.firstOrNull()
    if (beat == null) {
        world.gitAutoRuns[project]?.phase = "done"
        push(gitAutoFrame(project))
        return
    }
    after(beat.delay) {
        val run = world.gitAutoRuns[project] ?: return@after
        if (run.runId != runId || run.phase != "running") return@after
        if (run.canceling) {
            run.phase = "canceled"
            run.canceling = false
            for (step in run.steps) {
                if (step.status == "running") {
                    step.status = "pending"
                    step.detail = ""
                }
            }
        } else {
            beat.apply(run)
        }
        push(gitAutoFrame(project))
        if (run.phase == "running") playGitAuto(project, runId, beats.drop(1))
    }
}

private fun DemoServer.commitBeats(project: String, andPush: Boolean): List<Beat> {
    val beats = mutableListOf(
        Beat(0.2) { it.set("commit", "running", "Writing the commit message…") },
        Beat(1.8) { it.set("commit", "running", "Committing…") },
        Beat(0.5) { run ->
            val message = demoCommitAll(project)
            if (message == null) {
                run.set("commit", "failed")
                run.phase = "failed"
                run.error = "No changes to commit."
            } else {
                run.set("commit", "done", message)
                run.message = message
            }
        }
    )
    if (andPush) {
        val branch = world.git[project]?.branch ?: ""
        beats += Beat(0.2) { it.set("push", "running", "Pushing $branch…") }
        beats += Beat(1.2) { run ->
            demoPushed(project)
            run.set("push", "done", "origin/$branch")
        }
    }
    return beats
}

private fun DemoServer.prBeats(project: String, repo: DemoWorld.GitRepo): List<Beat> {
    val needsBranch = repo.detached || repo.branch == repo.defaultBranch
    val hasChanges = repo.files.isNotEmpty()
    val needsPush = needsBranch || hasChanges || !repo.hasUpstream || repo.ahead > 0
    if (needsBranch && !hasChanges) {
        return listOf(
            Beat(0.3) { run ->
                run.phase = "failed"
                run.error =
                    "Nothing to open a pull request for: no changes on ${repo.defaultBranch}."
            }
        )
    }
    val (draftTitle, _) = demoPrDraft(repo)
    val branch = if (needsBranch) "feat/" + demoSlug(draftTitle) else repo.branch
    val beats = mutableListOf(
        Beat(0.2) { run ->
            if (!needsBranch) run.set("branch", "skipped", "Already on ${repo.branch}")
            if (!hasChanges) run.set("commit", "skipped", "No uncommitted changes")
            if (!needsPush) run.set("push", "skipped", "Already pushed")
        }
    )
    if (needsBranch) {
        beats += Beat(0.2) { it.set("branch", "running", "Naming the branch…") }
        beats += Beat(1.5) { it.set("branch", "running", "Creating $branch…") }
        beats += Beat(0.4) { run ->
            demoCreateBranch(project, branch)
            run.set("branch", "done", branch)
            run.branch = branch
        }
    }
    if (hasChanges) {
        beats += Beat(0.2) { it.set("commit", "running", "Writing the commit message…") }
        beats += Beat(1.6) { it.set("commit", "running", "Committing…") }
        beats += Beat(0.5) { it.set("commit", "done", demoCommitAll(project) ?: "") }
    }
    if (needsPush) {
        beats += Beat(0.2) { it.set("push", "running", "Pushing $branch…") }
        beats += Beat(1.2) { run ->
            demoPushed(project)
            run.set("push", "done", "origin/$branch")
        }
    }
    beats += Beat(0.2) { it.set("pr", "running", "Writing the title and description…") }
    beats += Beat(2.0) { it.set("pr", "running", "Opening the pull request…") }
    beats += Beat(1.0) { run ->
        run.set("pr", "done", draftTitle)
        run.url = "https://github.com/demo/storefront/pull/42"
        run.title = draftTitle
        run.branch = branch
        run.base = repo.defaultBranch
    }
    return beats
}

/** Commit every changed file; the message, or null with nothing to commit. */
private fun DemoServer.demoCommitAll(project: String): String? {
    val repo = world.git[project]?.takeIf { it.files.isNotEmpty() } ?: return null
    val message = demoCommitMessage(repo.files.map { it.path })
    repo.files = emptyList()
    repo.ahead += 1
    pushGitChanged(project)
    return message
}

private fun DemoServer.demoPushed(project: String) {
    val repo = world.git[project] ?: return
    repo.ahead = 0
    repo.hasUpstream = true
    repo.aheadByBranch += repo.branch to 0
}

private fun DemoServer.demoCreateBranch(project: String, name: String) {
    val repo = world.git[project] ?: return
    repo.aheadByBranch += repo.branch to repo.ahead
    repo.branches =
        listOf(DemoWorld.GitBranch(name = name, committerDate = isoNow())) + repo.branches
    repo.branch = name
    repo.detached = false
    repo.ahead = 0
    repo.hasUpstream = false
}

private fun demoSlug(s: String): String {
    val parts = mutableListOf<String>()
    val current = StringBuilder()
    s.lowercase().codePoints().forEach { scalar ->
        if (isSwiftAlphanumeric(scalar)) {
            current.appendCodePoint(scalar)
        } else {
            parts.add(current.toString())
            current.clear()
        }
    }
    parts.add(current.toString())
    return parts.filter { it.isNotEmpty() }.joinToString("-")
}
