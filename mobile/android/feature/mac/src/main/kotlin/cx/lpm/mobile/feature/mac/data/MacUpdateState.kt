package cx.lpm.mobile.feature.mac.data

/** Where an install of lpm on the Mac stands, as this phone follows it. */
sealed interface MacUpdatePhase {
    data object Checking : MacUpdatePhase

    /** Whole percent; -1 until the size is known. */
    data class Downloading(val percent: Int) : MacUpdatePhase

    /** Replacing the app, past the point it can be cancelled. */
    data object Installing : MacUpdatePhase

    /** The connection dropped mid-install: lpm is restarting there. */
    data object Restarting : MacUpdatePhase

    /** Back on the new release. */
    data class Updated(val release: String) : MacUpdatePhase

    val inProgress: Boolean get() = this !is Updated

    val cancellable: Boolean get() = this == Checking || this is Downloading
}

/** A check asked for from the menu, until it turns up a release to offer. */
sealed interface MacUpdateLookup {
    data object Checking : MacUpdateLookup

    data object UpToDate : MacUpdateLookup

    data class Failed(val message: String) : MacUpdateLookup
}

/**
 * lpm on the Mac (iOS `MacUpdateStore`'s state): the release it runs, a newer one
 * when there is one, and an install of it there, whoever started it.
 */
data class MacUpdateState(
    val version: String? = null,
    val canUpdate: Boolean = false,
    /** The release the Mac could move to; null while it is up to date. */
    val newer: String? = null,
    val phase: MacUpdatePhase? = null,
    val cancelling: Boolean = false,
    /** The bar was closed for this release, so it stays away until the next one. */
    val dismissed: String? = null,
    val lookup: MacUpdateLookup? = null,
    val error: String? = null
) {
    val canInstall: Boolean get() = canUpdate && newer != null && phase?.inProgress != true

    val canCheck: Boolean
        get() = canUpdate && lookup != MacUpdateLookup.Checking && phase?.inProgress != true

    val showsBar: Boolean
        get() {
            if (phase != null || error != null || lookup != null) return true
            return canUpdate && newer != null && newer != dismissed
        }

    /** A failed install's message, once nothing runs any more. */
    val failure: String? get() = error?.takeIf { phase == null }
}
