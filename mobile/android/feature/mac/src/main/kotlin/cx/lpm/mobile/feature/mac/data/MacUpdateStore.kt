package cx.lpm.mobile.feature.mac.data

import cx.lpm.mobile.core.data.client.LpmClient
import cx.lpm.mobile.core.data.client.updateCancel
import cx.lpm.mobile.core.data.client.updateCheck
import cx.lpm.mobile.core.data.client.updateInstall
import cx.lpm.mobile.core.data.link.macLabel
import cx.lpm.mobile.core.data.prefs.PreferenceKeys
import cx.lpm.mobile.core.data.projects.ProjectsStore
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.data.session.SessionStore
import cx.lpm.mobile.core.model.BusyAgentPlace
import cx.lpm.mobile.core.model.MacBuild
import cx.lpm.mobile.core.model.MacUpdateCheck
import cx.lpm.mobile.core.model.MacUpdateStep
import cx.lpm.mobile.core.model.busyAgentPlaces
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * lpm on the session's Mac: the release it runs, a newer one when there is one, and
 * an install of it there — started from this phone, another phone, or the Mac
 * itself. The Mac owns the install; this mirrors what it reports (iOS `MacUpdateStore`).
 */
class MacUpdateStore(private val session: MacSession) : SessionStore {
    private val mutable = MutableStateFlow(MacUpdateState())
    val state: StateFlow<MacUpdateState> = mutable.asStateFlow()

    // What the Mac ran when the install began. Coming back on anything else is the
    // update having landed.
    private var from: String? = null
    private var giveUp: Job? = null
    private var fade: Job? = null
    private var lookupFade: Job? = null

    /** Where agents are mid-task now, which the restart an update ends in would cut off. */
    val busyPlaces: List<BusyAgentPlace>
        get() = busyAgentPlaces(session.store<ProjectsStore>().state.value.projects)

    override fun wire(client: LpmClient) {
        client.onBuild = { build ->
            if (client === session.client && !session.state.value.demoMode) noteBuild(build)
        }
        client.onUpdateCheck = { if (client === session.client) apply(it) }
        client.onUpdateProgress = { if (client === session.client) apply(it) }
        client.onUpdateInstall = { _, error -> if (client === session.client) finished(error) }
        client.onUpdateCancel = { stopping ->
            if (client === session.client &&
                !stopping
            ) {
                mutable.update { it.copy(cancelling = false) }
            }
        }
    }

    override fun reset() {
        cancelTimers()
        from = null
        mutable.value = MacUpdateState()
    }

    override fun onConnectionReset() {
        mutable.update { it.copy(cancelling = false) }
        clearLookup()
        if (state.value.phase != MacUpdatePhase.Installing) return
        mutable.update { it.copy(phase = MacUpdatePhase.Restarting) }
        giveUp?.cancel()
        giveUp = session.scope.launch {
            delay(RESTART_GRACE_MS)
            if (state.value.phase == MacUpdatePhase.Restarting) settle()
        }
    }

    // MARK: from the Mac

    fun noteBuild(build: MacBuild?) {
        val current = build?.version
        val origin = from
        if (current != null && origin != null && current != origin &&
            state.value.phase?.inProgress == true
        ) {
            land(current)
        }
        val canUpdate = build?.selfUpdate ?: false
        mutable.update {
            it.copy(
                version = current,
                canUpdate = canUpdate,
                dismissed = session.preferences.string(dismissKey),
                newer = if (canUpdate) it.newer else null
            )
        }
        if (canUpdate) session.client?.updateCheck()
    }

    fun apply(check: MacUpdateCheck) {
        mutable.update {
            it.copy(
                version = check.currentVersion ?: it.version,
                newer = if (check.updateAvailable) check.latestVersion else null
            )
        }
        if (state.value.lookup == MacUpdateLookup.Checking) answer(check)
        apply(check.step, caughtUp = true)
    }

    /**
     * `caughtUp` is the Mac answering a reconnect: idle then means an install this
     * phone was following ended while it was away.
     */
    fun apply(step: MacUpdateStep, caughtUp: Boolean = false) {
        when (step.phase) {
            MacUpdateStep.Phase.Checking -> follow(MacUpdatePhase.Checking)

            MacUpdateStep.Phase.Downloading -> follow(MacUpdatePhase.Downloading(step.progress))

            MacUpdateStep.Phase.Installing -> {
                mutable.update { it.copy(cancelling = false) }
                follow(MacUpdatePhase.Installing)
            }

            MacUpdateStep.Phase.Idle -> {
                val phase = state.value.phase ?: return
                if (!phase.inProgress) return
                if (caughtUp &&
                    (phase == MacUpdatePhase.Installing || phase == MacUpdatePhase.Restarting)
                ) {
                    val mac = session.link.macLabel()
                    mutable.update {
                        it.copy(error = "The update didn't finish on $mac. Try again.")
                    }
                }
                settle()
            }
        }
    }

    fun finished(error: String?) {
        if (error != null) mutable.update { it.copy(error = error) }
        if (state.value.phase?.inProgress == true) settle()
    }

    // MARK: from this phone

    /** Asks the Mac for the newest release now, past the hour it keeps an answer. */
    fun checkNow() {
        if (!state.value.canCheck) return
        val client = session.client ?: return
        lookupFade?.cancel()
        mutable.update { it.copy(error = null, lookup = MacUpdateLookup.Checking) }
        client.updateCheck(force = true)
    }

    fun install() {
        if (!state.value.canInstall) return
        val client = session.client ?: return
        clearLookup()
        from = state.value.version
        mutable.update {
            it.copy(error = null, cancelling = false, phase = MacUpdatePhase.Checking)
        }
        client.updateInstall()
    }

    fun cancel() {
        val s = state.value
        if (s.phase?.cancellable != true || s.cancelling) return
        mutable.update { it.copy(cancelling = true) }
        session.client?.updateCancel()
    }

    fun dismiss() {
        val s = state.value
        when {
            s.lookup != null -> clearLookup()

            s.error != null -> mutable.update { it.copy(error = null) }

            else -> {
                mutable.update { it.copy(dismissed = s.newer) }
                session.preferences.setString(dismissKey, s.newer)
            }
        }
    }

    // MARK: -

    private val dismissKey: String
        get() {
            val s = session.state.value
            return PreferenceKeys.macUpdateDismissed(
                s.activeRecord?.serverId ?: s.activeMacId.orEmpty()
            )
        }

    /** A release turned up brings the bar back even if it was closed for it. */
    private fun answer(check: MacUpdateCheck) {
        val error = check.error
        when {
            error != null -> mutable.update { it.copy(lookup = MacUpdateLookup.Failed(error)) }

            state.value.newer != null -> {
                mutable.update { it.copy(lookup = null, dismissed = null) }
                session.preferences.remove(dismissKey)
            }

            else -> {
                mutable.update { it.copy(lookup = MacUpdateLookup.UpToDate) }
                lookupFade = session.scope.launch {
                    delay(UP_TO_DATE_FOR_MS)
                    if (state.value.lookup == MacUpdateLookup.UpToDate) clearLookup()
                }
            }
        }
    }

    private fun clearLookup() {
        lookupFade?.cancel()
        mutable.update { it.copy(lookup = null) }
    }

    private fun follow(next: MacUpdatePhase) {
        clearLookup()
        if (from == null) from = state.value.version
        giveUp?.cancel()
        fade?.cancel()
        mutable.update { it.copy(phase = next) }
    }

    private fun settle() {
        giveUp?.cancel()
        from = null
        mutable.update { it.copy(phase = null, cancelling = false) }
    }

    private fun land(release: String) {
        settle()
        mutable.update {
            it.copy(newer = null, error = null, phase = MacUpdatePhase.Updated(release))
        }
        fade = session.scope.launch {
            delay(UPDATED_FOR_MS)
            if (state.value.phase is MacUpdatePhase.Updated) {
                mutable.update { it.copy(phase = null) }
            }
        }
    }

    private fun cancelTimers() {
        giveUp?.cancel()
        fade?.cancel()
        lookupFade?.cancel()
    }

    internal companion object {
        /**
         * How long a Mac restarting lpm has to come back before the bar stops saying
         * so; the connection status explains the rest.
         */
        const val RESTART_GRACE_MS = 180_000L
        const val UPDATED_FOR_MS = 8_000L
        const val UP_TO_DATE_FOR_MS = 5_000L
    }
}
