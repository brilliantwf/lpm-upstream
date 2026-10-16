package cx.lpm.mobile.core.demo

/**
 * Demo handlers for auto pull: a project's own choice, and undoing the last auto pull
 * (the commits it brought in show up as behind again, as after the Mac's
 * `git reset --keep`).
 */
internal fun DemoServer.registerAutoPullHandlers() {
    register("setAutoPull") { o ->
        val project = o.string("project") ?: return@register
        world.projectIndex(project)?.let { world.projects[it].autoPull = o.bool("value") }
        push(mapOf("t" to "setAutoPull", "project" to project, "ok" to true))
        pushAfter(0.1) { mapOf("t" to "projects-changed") }
    }
    register("gitAutoPullUndo") { o ->
        val project = o.string("project") ?: return@register
        pushAfter(0.8) {
            val repo = world.git[project]
            val pulled = repo?.autoPulled ?: return@pushAfter mapOf(
                "t" to "gitAutoPullUndo",
                "project" to project,
                "ok" to false,
                "error" to "There's no auto pull to undo."
            )
            repo.autoPulled = null
            repo.behind += pulled.int("count") ?: 0
            pushGitChanged(project)
            mapOf("t" to "gitAutoPullUndo", "project" to project, "ok" to true)
        }
    }
}
