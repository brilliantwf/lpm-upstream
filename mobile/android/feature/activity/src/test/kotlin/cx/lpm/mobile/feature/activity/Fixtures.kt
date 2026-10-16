package cx.lpm.mobile.feature.activity

import cx.lpm.mobile.core.model.AutomationJob
import cx.lpm.mobile.core.model.ClaudeOwnChoice
import cx.lpm.mobile.core.model.Profile
import cx.lpm.mobile.core.model.Project
import cx.lpm.mobile.core.model.Service
import cx.lpm.mobile.core.model.StatusEntry

const val NOW = 1_760_000_000_000L

fun project(
    name: String,
    running: Boolean = false,
    services: List<Service> = emptyList(),
    started: List<String> = emptyList(),
    profiles: List<Profile> = emptyList(),
    activeProfile: String = "",
    statusEntries: List<StatusEntry> = emptyList(),
    parent: String = "",
    worktree: Boolean = false,
    remote: Boolean = false
) = Project(
    name = name,
    label = name,
    running = running,
    isRemote = remote,
    parentName = parent,
    worktree = worktree,
    workStatus = null,
    autoPull = null,
    claude = ClaudeOwnChoice(),
    statusEntries = statusEntries,
    services = services.filter { it.name in started },
    allServices = services,
    profiles = profiles,
    activeProfile = activeProfile,
    actions = emptyList(),
    zones = emptyList(),
    configError = null
)

fun status(key: String, value: String, pane: String = "t1", at: Long = NOW - 60_000) = StatusEntry(
    key = key,
    value = value,
    priority = 0,
    timestamp = at,
    paneID = pane,
    turnStart = 0,
    endedAt = 0
)

fun job(
    id: String,
    project: String = "",
    targets: List<String> = emptyList(),
    targetCount: Int = targets.size,
    runningCount: Int = 0,
    runningSince: Long? = (NOW - 120_000) / 1000,
    standalone: Boolean = false,
    running: Boolean = true
) = AutomationJob(
    id = id,
    project = project,
    valid = true,
    source = "project",
    error = "",
    label = id.replaceFirstChar { it.uppercase() },
    emoji = "",
    enabled = true,
    duplicate = false,
    runKind = "prompt",
    summary = "",
    scheduleMode = "every",
    cron = "",
    everySecs = 3600,
    atMinutes = 0,
    days = emptyList(),
    everyMaxSecs = 0,
    untilMinutes = null,
    times = 0,
    pickDays = 0,
    alsoAtMinutes = emptyList(),
    monthDays = emptyList(),
    lastDay = false,
    shortMonths = "",
    nth = "",
    nthOf = "",
    onDates = emptyList(),
    fromMinutes = null,
    lastRunAt = null,
    lastResult = "",
    nextFireAt = null,
    running = running,
    runningSince = runningSince,
    agent = "",
    model = "",
    effort = "",
    targets = targets,
    targetCount = targetCount,
    runningCount = runningCount,
    standalone = standalone,
    unread = 0
)

fun build(
    projects: List<Project>,
    jobs: List<AutomationJob> = emptyList(),
    titles: Map<String, Map<String, String>> = emptyMap(),
    machines: List<ActivityMachine> = emptyList()
): Activity = buildActivity(projects, machines, jobs, NOW, titles)
