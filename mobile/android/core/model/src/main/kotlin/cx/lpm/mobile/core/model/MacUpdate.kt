package cx.lpm.mobile.core.model

import kotlinx.serialization.json.JsonObject

/** The lpm release a Mac runs, from its `paired` and `ready` frames. */
data class MacBuild(
    val version: String,
    /** Only a release build on macOS replaces itself, so only then can this phone update it. */
    val selfUpdate: Boolean
) {
    companion object {
        fun parse(o: JsonObject): MacBuild? {
            val version = o.string("version")?.ifEmpty { null } ?: return null
            return MacBuild(version = version, selfUpdate = o.bool("selfUpdate") ?: false)
        }
    }
}

/** Where an install on the Mac stands. Always the whole state, never a delta. */
data class MacUpdateStep(
    val phase: Phase,
    /** Whole percent while downloading; -1 until the size is known. */
    val progress: Int
) {
    enum class Phase(val rawValue: String) {
        Idle("idle"),
        Checking("checking"),
        Downloading("downloading"),
        Installing("installing")
        ;

        companion object {
            fun of(rawValue: String?): Phase? = entries.firstOrNull { it.rawValue == rawValue }
        }
    }

    companion object {
        fun parse(o: JsonObject): MacUpdateStep = MacUpdateStep(
            phase = Phase.of(o.string("phase").orEmpty()) ?: Phase.Idle,
            progress = o.int("progress") ?: -1
        )
    }
}

/**
 * The `updateCheck` reply: the newest release, and the install running now. A
 * failed check still says what the Mac is doing.
 */
data class MacUpdateCheck(
    val currentVersion: String?,
    val latestVersion: String?,
    val updateAvailable: Boolean,
    val error: String?,
    val step: MacUpdateStep
) {
    companion object {
        fun parse(o: JsonObject): MacUpdateCheck {
            val ok = o.bool("ok") ?: false
            return MacUpdateCheck(
                currentVersion = if (ok) o.string("currentVersion") else null,
                latestVersion = if (ok) o.string("latestVersion") else null,
                updateAvailable = ok && (o.bool("updateAvail") ?: false),
                error = if (ok) null else o.string("error") ?: "The check didn't go through.",
                step = MacUpdateStep.parse(o)
            )
        }
    }
}
