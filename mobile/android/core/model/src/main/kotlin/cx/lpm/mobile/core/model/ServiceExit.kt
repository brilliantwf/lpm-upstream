package cx.lpm.mobile.core.model

// How a started service ended, as the desktop reads it (serviceExit.ts). The words
// for it ("exited with code 1") live with the screens that show them.

/** Ended on its own with a failure; an exit the shell couldn't report isn't one. */
val Service.crashed: Boolean get() = exited && exitCode.let { it != null && it != 0 }

/** The started services that are still running. */
val Project.liveServiceNames: Set<String>
    get() = if (running) services.filterNot { it.exited }.map { it.name }.toSet() else emptySet()

/**
 * The started services whose command ended, by name. Their panes and logs stay until
 * they're restarted or stopped.
 */
val Project.endedServices: Map<String, Service>
    get() = if (running) {
        services.filter { it.exited }.distinctBy { it.name }.associateBy { it.name }
    } else {
        emptyMap()
    }

/**
 * Something of the project still runs: once every service it started has ended, it's
 * up only in that their tabs are still open.
 */
val Project.isUp: Boolean get() = running && (services.isEmpty() || services.any { !it.exited })

/** The started services that crashed, in the project's order; empty while it's stopped. */
val Project.crashedServices: List<Service>
    get() = if (running) services.filter { it.crashed } else emptyList()
