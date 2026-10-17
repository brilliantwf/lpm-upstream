package cx.lpm.mobile.core.data.push

import cx.lpm.mobile.core.data.client.LpmClient
import cx.lpm.mobile.core.data.client.LpmClientFactory
import cx.lpm.mobile.core.data.client.sendPushToken
import cx.lpm.mobile.core.data.di.ApplicationScope
import cx.lpm.mobile.core.data.link.HostProber
import cx.lpm.mobile.core.data.mac.CredentialStore
import cx.lpm.mobile.core.data.mac.MacRecord
import cx.lpm.mobile.core.data.mac.ThisDevice
import cx.lpm.mobile.core.data.prefs.AppPreferences
import cx.lpm.mobile.core.data.prefs.PreferenceKeys
import cx.lpm.mobile.core.data.prefs.notificationPrefs
import cx.lpm.mobile.core.data.prefs.notificationPrefsFlow
import cx.lpm.mobile.core.data.session.MacSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps every saved Mac holding this phone's push identity (iOS `AppModel`'s push
 * half plus `PushRegistrar`). A Mac only pushes to a phone whose token it holds, and
 * the live socket reaches one Mac at a time, so the registration goes over the live
 * link when it comes up and a sweep opens a short-lived connection to each other
 * saved Mac. A Mac that acknowledged the current registration is remembered (by its
 * [PushRegistration.signature]) and skipped until something it holds changes.
 *
 * Nothing runs before [start], which the app calls once the stored keys are loaded:
 * reading the push key earlier would mint a new one over the saved one.
 */
@Singleton
class PushRegistrar @Inject constructor(
    private val tokens: ResolvedPush,
    private val credentials: CredentialStore,
    private val prefs: AppPreferences,
    private val clients: LpmClientFactory,
    private val prober: HostProber,
    private val device: ThisDevice,
    @param:ApplicationScope private val scope: CoroutineScope
) {
    private var session: MacSession? = null
    private var started = false
    private var allowed = false
    private var lastSent: PushRegistration? = null
    private var pending: Job? = null
    private var sweeping = false

    // A sweep asked for while one runs: the preferences may have moved under it.
    private var rerun = false

    /** The live session, attached by its push store as it's built. */
    internal fun attach(session: MacSession) {
        this.session = session
    }

    fun start() {
        val s = session ?: return
        if (started) return
        started = true
        allowed = tokens.source.notificationsAllowed()
        scope.launch {
            combine(tokens.source.token, prefs.notificationPrefsFlow()) { token, p -> token to p }
                .distinctUntilChanged()
                .collect { sync() }
        }
        // A Mac added (paired, imported) or removed changes who the sweep covers.
        scope.launch {
            s.state.map { st -> st.macs.map { it.localId to it.via } }
                .distinctUntilChanged()
                .drop(1)
                .collect { schedule() }
        }
    }

    /** The app came back on screen: notifications may have been allowed meanwhile. */
    fun refreshPermission() {
        if (!started) return
        val now = tokens.source.notificationsAllowed()
        if (now == allowed) return
        allowed = now
        sync()
    }

    /** This phone's push identity changed or a link came up: register everywhere. */
    fun sync() {
        sendLive()
        schedule()
    }

    /** What every Mac is told; null until there's a token and permission to post. */
    fun registration(): PushRegistration? {
        if (!started) return null
        val token = tokens.source.token.value ?: return null
        if (!tokens.source.notificationsAllowed()) return null
        return PushRegistration.of(
            tokens.source.platform,
            token,
            credentials.loadOrCreatePushKey(),
            prefs.notificationPrefs
        )
    }

    /**
     * Over the live link. A machine opened through another Mac doesn't know this
     * phone (that Mac is registered by the sweep), and Demo Mode has no push at all.
     */
    private fun sendLive() {
        val s = session ?: return
        val state = s.state.value
        val client = s.client ?: return
        if (!state.isReady || state.demoMode || state.activeRecord?.via != null) return
        val registration = registration() ?: return
        lastSent = registration
        client.sendPushToken(registration)
    }

    /** The live Mac's ack: remember it, so the sweep doesn't dial that Mac again. */
    internal fun liveAcknowledged(ok: Boolean) {
        val state = session?.state?.value ?: return
        val id = state.activeMacId ?: return
        val registration = lastSent ?: return
        if (ok && !state.demoMode) {
            prefs.setString(PreferenceKeys.pushRegistered(id), registration.signature)
        }
    }

    /** A burst of changes collapses into one sweep shortly after the last. */
    private fun schedule() {
        if (!started) return
        pending?.cancel()
        pending = scope.launch {
            delay(COALESCE_MS)
            launchSweep()
        }
    }

    // Its own job, so a later `schedule` can't cancel dials already under way.
    private fun launchSweep() {
        if (sweeping) {
            rerun = true
            return
        }
        sweeping = true
        scope.launch {
            try {
                do {
                    rerun = false
                    sweepOnce()
                } while (rerun)
            } finally {
                sweeping = false
            }
        }
    }

    /** One pass, one Mac at a time: nothing waits on it, and no burst of dials. */
    private suspend fun sweepOnce() {
        val s = session ?: return
        val state = s.state.value
        if (state.demoMode) return
        val registration = registration() ?: return
        for (mac in state.macs) {
            if (mac.localId == state.activeMacId || mac.via != null) continue
            val key = PreferenceKeys.pushRegistered(mac.localId)
            if (prefs.string(key) == registration.signature) continue
            if (register(s, mac, registration)) prefs.setString(key, registration.signature)
        }
    }

    /** False covers unreachable, refused and timed out alike: the next sweep retries. */
    private suspend fun register(
        s: MacSession,
        mac: MacRecord,
        registration: PushRegistration
    ): Boolean {
        val credential = credentials.load(mac.localId) ?: return false
        val identity = HostProber.Identity(pin = credentials.loadPin(mac.localId))
        val hosts = mac.hosts.filter { it.isNotEmpty() }
        val host = if (hosts.size == 1) {
            hosts.single().takeIf { prober.open(it, mac.port, identity, PROBE_MS).reachable }
        } else {
            prober.race(hosts, mac.port, identity, PROBE_MS).winner
        } ?: return false
        return PushAttempt(clients, device.name).register(
            endpoint = LpmClient.Endpoint(host, mac.port),
            credential = credential,
            pin = { credentials.loadPin(mac.localId) },
            registration = registration,
            onIdentity = { serverId, serverName, platform ->
                s.learnIdentity(mac.localId, serverId, serverName, platform)
            },
            timeoutMs = ATTEMPT_MS
        )
    }

    private companion object {
        const val COALESCE_MS = 1_500L

        // Generous: a Tailscale path can take seconds, and nothing waits on it.
        const val ATTEMPT_MS = 20_000L
        const val PROBE_MS = 6_000L
    }
}

/** The bound [PushTokenSource], or [NoPushTokens] until `:core:push` provides one. */
class ResolvedPush(val source: PushTokenSource)
