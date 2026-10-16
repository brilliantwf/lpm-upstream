package cx.lpm.mobile.feature.projects

import cx.lpm.mobile.core.model.Action
import cx.lpm.mobile.core.model.ClaudeOwnChoice
import cx.lpm.mobile.core.model.Project
import cx.lpm.mobile.core.model.Service
import cx.lpm.mobile.core.model.StatusEntry

fun project(
    name: String,
    running: Boolean = false,
    parent: String = "",
    services: List<String> = emptyList(),
    statusEntries: List<StatusEntry> = emptyList(),
    actions: List<Action> = emptyList(),
    remote: Boolean = false
) = Project(
    name = name,
    label = name,
    running = running,
    isRemote = remote,
    parentName = parent,
    worktree = false,
    workStatus = null,
    autoPull = null,
    claude = ClaudeOwnChoice(),
    statusEntries = statusEntries,
    services = if (running) services.map { Service(it, 0) } else emptyList(),
    allServices = services.map { Service(it, 0) },
    profiles = emptyList(),
    activeProfile = "",
    actions = actions,
    zones = emptyList(),
    configError = null
)
