package cx.lpm.mobile.core.data.link

import cx.lpm.mobile.core.data.client.LpmClient
import cx.lpm.mobile.core.data.link.NetworkMonitor.Medium
import cx.lpm.mobile.core.data.mac.MacRecord
import cx.lpm.mobile.core.data.prefs.PreferenceKeys
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.data.session.SessionEnvironment
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Everything about the link to the active Mac that the connection UI shows: the
 * issue in plain words, the line under the Mac's name, address checks, the queue
 * of actions waiting for the Mac, and the sheets and flows around them. The
 * session feeds it; screens read [state] and [views].
 */
class LinkStore internal constructor(
    internal val session: MacSession,
    internal val env: SessionEnvironment
) {
    private val mutable = MutableStateFlow(LinkState())
    val state: StateFlow<LinkState> = mutable.asStateFlow()

    private var slowTimer: Job? = null
    private var slowCeilingTimer: Job? = null
    private var tryingTimer: Job? = null
    private val queuedWork = mutableMapOf<String, (LpmClient) -> Unit>()
    private var reachCheckedAt: Instant? = null

    // Work to run once the sheet on screen has gone, for an action that opens
    // another sheet in its place.
    private var afterSheet: (() -> Unit)? = null

    /** The derived view, recomputed whenever anything it reads changes. */
    val views: Flow<LinkView> = combine(
        listOf(
            session.state,
            mutable,
            session.clientFlow,
            env.network.network,
            env.tailnet.enabled,
            env.tailnet.state,
            env.prefs.data
        )
    ) { view() }.distinctUntilChanged()

    /** For the screens' own flags (sheets open, the summary shown, …). */
    fun update(change: (LinkState) -> LinkState) = mutable.update(change)

    internal val now: Instant get() = env.clock.instant()

    val isReady: Boolean get() = session.state.value.isReady

    // MARK: updates from the session

    /** The connection moved from `old` to `new`. */
    fun noteState(new: LpmClient.State, old: LpmClient.State) {
        when (new) {
            LpmClient.State.Connecting -> {
                if (old == LpmClient.State.Connecting) return
                // A retry after a failure is already slow; restarting the clock
                // would flip the line back on every attempt.
                if (old is LpmClient.State.Failed) {
                    mutable.update { it.copy(slowConnect = true) }
                    return
                }
                mutable.update { it.copy(slowConnect = false) }
                armSlow(SLOW_AFTER_MS, waitingOnTailnet = true)
                slowCeilingTimer?.cancel()
                slowCeilingTimer = env.scope.launch {
                    delay(SLOW_CEILING_MS)
                    if (session.state.value.connection == LpmClient.State.Connecting) {
                        mutable.update { it.copy(slowConnect = true) }
                    }
                }
            }

            LpmClient.State.Ready -> {
                cancelSlow()
                endTrying()
                mutable.update { link ->
                    link.copy(
                        slowConnect = false,
                        connectedSince = now,
                        reconnectReason = null,
                        probedAllFailed = false,
                        identityRejected = false,
                        lastFailure = null,
                        checks =
                            link.host?.let { link.checks + (it to AddressCheck(true, "ok", now)) }
                                ?: link.checks
                    )
                }
            }

            is LpmClient.State.Failed -> {
                cancelSlow()
                val raw = new.message
                if (LpmClient.isOfflineHint(raw) || raw == LpmClient.RELAY_OFFLINE_ERROR) {
                    mutable.update { it.copy(lastFailure = raw) }
                }
                endTrying()
            }

            LpmClient.State.Idle -> cancelSlow()
        }
        if (new != LpmClient.State.Ready) mutable.update { it.copy(connectedSince = null) }
    }

    // Built-in Tailscale coming back after a suspend is part of a normal open, so a
    // connect waiting on it starts its count once it's up.
    private fun armSlow(delayMs: Long, waitingOnTailnet: Boolean) {
        slowTimer?.cancel()
        slowTimer = env.scope.launch {
            delay(delayMs)
            if (session.state.value.connection != LpmClient.State.Connecting) return@launch
            if (waitingOnTailnet && env.tailnet.enabled.value &&
                env.tailnet.state.value == "starting"
            ) {
                return@launch
            }
            mutable.update { it.copy(slowConnect = true) }
        }
    }

    private fun cancelSlow() {
        slowTimer?.cancel()
        slowCeilingTimer?.cancel()
    }

    fun tailnetCameUp() {
        if (session.state.value.connection != LpmClient.State.Connecting ||
            state.value.slowConnect
        ) {
            return
        }
        armSlow(SLOW_AFTER_MS, waitingOnTailnet = false)
    }

    fun clearFailure() = mutable.update { it.copy(lastFailure = null, probedAllFailed = false) }

    fun noteTrying() {
        mutable.update { it.copy(trying = true) }
        tryingTimer?.cancel()
        tryingTimer = env.scope.launch {
            delay(TRYING_MS)
            mutable.update { it.copy(trying = false) }
        }
    }

    private fun endTrying() {
        tryingTimer?.cancel()
        mutable.update { it.copy(trying = false) }
    }

    fun noteProbe(outcomes: List<HostProber.Outcome>) {
        if (outcomes.isEmpty()) return
        noteChecks(outcomes)
        val allFailed = outcomes.none { it.reachable }
        mutable.update { it.copy(probedAllFailed = allFailed) }
        if (!allFailed) endTrying()
    }

    fun noteChecks(outcomes: List<HostProber.Outcome>) {
        val at = now
        val fresh = outcomes.filter { it.detail != "cancelled" }
            .associate { it.host to AddressCheck(it.reachable, it.detail, at) }
        mutable.update { it.copy(checks = it.checks + fresh) }
    }

    /**
     * Check each address on its own, for the lists that show every result; `mac` is
     * the saved Mac they belong to, whose certificate the probes expect.
     */
    suspend fun test(hosts: List<String>, port: Int, updatingIssue: Boolean, mac: MacRecord?) {
        mutable.update { it.copy(testing = it.testing + hosts) }
        val outcomes = env.prober.checkAll(hosts, port, session.probeIdentity(mac))
        mutable.update { it.copy(testing = it.testing - hosts.toSet()) }
        if (updatingIssue && !isReady) noteProbe(outcomes) else noteChecks(outcomes)
    }

    fun resetSession() {
        cancelSlow()
        endTrying()
        queuedWork.clear()
        mutable.update {
            it.copy(
                host = null,
                pairing = false,
                checks = emptyMap(),
                probedAllFailed = false,
                connectedSince = null,
                slowConnect = false,
                reconnectReason = null,
                lastFailure = null,
                listAsOf = null,
                queued = emptyList(),
                identityRejected = false,
                newFingerprint = null,
                identityCheckOpen = false,
                tailscaleFailure = null
            )
        }
    }

    fun afterSheetCloses(work: () -> Unit) {
        afterSheet = work
    }

    fun runAfterSheet() {
        val work = afterSheet
        afterSheet = null
        work?.invoke()
    }

    // MARK: actions waiting for the Mac

    fun enqueue(verb: String, project: String, work: (LpmClient) -> Unit) {
        val action = QueuedAction(project = project, verb = verb, queuedAt = now)
        mutable.update { it.copy(queued = it.queued + action) }
        queuedWork[action.id] = work
        armExpiry(action.id)
    }

    /** Send everything still waiting, in the order it was tapped. */
    fun flushQueued(client: LpmClient) {
        for (action in state.value.queued) {
            if (action.expired) continue
            queuedWork.remove(action.id)?.invoke(client)
        }
        mutable.update { link -> link.copy(queued = link.queued.filter { it.expired }) }
    }

    fun cancelQueued(id: String) {
        mutable.update { link -> link.copy(queued = link.queued.filterNot { it.id == id }) }
        queuedWork.remove(id)
    }

    fun retryQueued(id: String) {
        if (state.value.queued.none { it.id == id }) return
        val client = session.client
        if (isReady && client != null) {
            queuedWork[id]?.invoke(client)
            cancelQueued(id)
            return
        }
        mutable.update { link ->
            link.copy(
                queued = link.queued.map {
                    if (it.id ==
                        id
                    ) {
                        it.copy(expired = false, queuedAt = now)
                    } else {
                        it
                    }
                }
            )
        }
        armExpiry(id)
    }

    private fun armExpiry(id: String) {
        env.scope.launch {
            delay(QueuedAction.LIFETIME_MS)
            val action = state.value.queued.firstOrNull { it.id == id } ?: return@launch
            val waited = Duration.between(action.queuedAt, now).toMillis()
            if (waited < QueuedAction.LIFETIME_MS - 1_000) return@launch
            mutable.update { link ->
                link.copy(
                    queued = link.queued.map {
                        if (it.id ==
                            id
                        ) {
                            it.copy(expired = true)
                        } else {
                            it
                        }
                    }
                )
            }
        }
    }

    // MARK: other saved machines

    /** Check which of the other saved machines answer, at most every 45 seconds. */
    fun refreshReach(force: Boolean = false) {
        val s = session.state.value
        if (s.demoMode || session.isVisit) return
        val last = reachCheckedAt
        if (!force && last != null &&
            Duration.between(last, now).toMillis() < REACH_INTERVAL_MS
        ) {
            return
        }
        reachCheckedAt = now
        for (mac in s.macs) {
            if (mac.localId == s.activeMacId) continue
            val id = mac.localId
            if (state.value.reach[id] == null) {
                mutable.update { it.copy(reach = it.reach + (id to MacReach.Checking)) }
            }
            val target = s.linkRecord(mac)
            env.scope.launch {
                val up = env.prober.anyReachable(
                    target.hosts,
                    target.port,
                    session.probeIdentity(mac)
                )
                val reach = if (up) MacReach.Online else MacReach.Offline
                mutable.update { it.copy(reach = it.reach + (id to reach)) }
            }
        }
    }

    fun forgetReach(id: String) = mutable.update { it.copy(reach = it.reach - id) }

    // MARK: Built-in Tailscale from the problem

    /**
     * Turn Built-in Tailscale on and open its sign-in page when it has one, so
     * fixing "away from home" is one tap from where the problem shows.
     */
    fun turnOnTailscale() {
        if (state.value.tailscaleWorking) return
        mutable.update { it.copy(tailscaleWorking = true, tailscaleFailure = null) }
        env.scope.launch {
            try {
                val page = env.tailnet.signIn()
                if (page != null) mutable.update { it.copy(tailscalePage = page) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(tailscaleFailure = e.message ?: e.toString()) }
            } finally {
                mutable.update { it.copy(tailscaleWorking = false) }
            }
        }
    }

    private companion object {
        /** How long a connect runs before the line says it's still going. */
        const val SLOW_AFTER_MS = 4_000L

        /** Past this it's slow even while Built-in Tailscale is still starting. */
        const val SLOW_CEILING_MS = 15_000L
        const val TRYING_MS = 8_000L
        const val REACH_INTERVAL_MS = 45_000L
    }
}
