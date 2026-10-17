package cx.lpm.mobile.core.tailnet

import cx.lpm.mobile.core.data.di.ApplicationScope
import cx.lpm.mobile.core.data.mac.ThisDevice
import cx.lpm.mobile.core.data.prefs.AppPreferences
import cx.lpm.mobile.core.data.prefs.PreferenceKeys
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * lpm Link's built-in Tailscale node (iOS `BuiltInTailscale`). It joins the user's
 * tailnet as a device of its own, so the phone reaches a Mac's tailnet address from
 * any network with no Tailscale app.
 *
 * The node lives in the app process, so Android freezes it with the app; after a
 * long freeze its sockets are dead and it restarts on the way back. A connection to
 * a tailnet address goes through a short-lived loopback bridge ([route]), so OkHttp
 * and certificate pinning work exactly as on the LAN. Node calls block, so they run
 * one at a time on [commands]; state changes on the main thread.
 */
@Singleton
class DefaultBuiltInTailscale @Inject constructor(
    private val node: TailnetNode,
    private val network: NetworkReporter,
    private val prefs: AppPreferences,
    private val clock: Clock,
    device: ThisDevice,
    visibility: AppVisibility,
    @param:TailnetDirectory private val stateDir: File,
    @param:TailnetCommands private val commands: CoroutineDispatcher,
    @param:ApplicationScope private val scope: CoroutineScope
) : BuiltInTailscale {
    private val mutableEnabled = MutableStateFlow(false)
    private val mutableStatus = MutableStateFlow(TailnetStatus())
    private val mutableState = MutableStateFlow(mutableStatus.value.state)

    override val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()
    override val status: StateFlow<TailnetStatus> = mutableStatus.asStateFlow()
    override val state: StateFlow<String> = mutableState.asStateFlow()
    override val deviceName: String = "lpm-" + device.noun.lowercase()

    // Read from whichever thread dials.
    private val routing = AtomicBoolean(false)
    private var started = false
    private var watcher: Thread? = null
    private var inForeground = false
    private var lastAwake: Instant? = null
    private var frozen = false
    private var heartbeat: Job? = null
    private var stateWhenBackgrounded: String? = null

    init {
        scope.launch {
            visibility.observe(onForeground = ::foreground, onBackground = ::background)
            // The preferences load after this is built; follow them once they do.
            prefs.data.collect { followSavedSwitch() }
        }
    }

    override fun setEnabled(on: Boolean) {
        prefs.setBool(PreferenceKeys.BUILT_IN_TAILSCALE_ENABLED, on)
        mutableEnabled.value = on
        if (on) start() else stop()
    }

    private fun followSavedSwitch() {
        val saved = prefs.bool(PreferenceKeys.BUILT_IN_TAILSCALE_ENABLED) ?: false
        if (saved == enabled.value) return
        mutableEnabled.value = saved
        if (!saved) {
            stop()
        } else if (inForeground) {
            start()
        }
    }

    /**
     * Bring the node up when the app comes on screen. A connected node that sat
     * frozen has dead sockets, so it restarts. One the app kept running in the
     * background (read-aloud playback) is left alone, as is one mid sign-in: a
     * restart would drop a live connection or abandon the pending sign-in.
     */
    fun foreground() {
        inForeground = true
        heartbeat?.cancel()
        heartbeat = null
        val awake = lastAwake
        val wasFrozen = frozen || (awake != null && sinceLongEnough(awake))
        val before = stateWhenBackgrounded
        lastAwake = null
        frozen = false
        stateWhenBackgrounded = null
        if (!enabled.value) return
        if (started && before == "running" && wasFrozen) restart() else start()
    }

    /**
     * Note the moment the app leaves the screen, and keep noting it for as long as
     * the process keeps running: a gap means Android froze it. The heartbeat checks
     * the gap itself, since an overdue tick can run before the app returns.
     */
    fun background() {
        inForeground = false
        stateWhenBackgrounded = status.value.state
        lastAwake = clock.instant()
        heartbeat?.cancel()
        heartbeat = scope.launch {
            while (true) {
                delay(HEARTBEAT.toMillis())
                lastAwake?.let { if (sinceLongEnough(it)) frozen = true }
                lastAwake = clock.instant()
            }
        }
    }

    private fun sinceLongEnough(then: Instant) =
        Duration.between(then, clock.instant()) > SUSPENDED_LONG_ENOUGH

    /**
     * Ask Tailscale for a sign-in page, turning the node on first if needed.
     * Returns the page to open, or null once the node is signed in (or waiting for
     * an admin) without one. A node that needs signing in asks for the page itself
     * as it starts, so this first waits for it to settle; only a node that was
     * signed out is asked for a new page.
     */
    override suspend fun signIn(): String? {
        if (!enabled.value) setEnabled(true)
        waitFor { it.authUrl != null || it.state !in setOf("off", "starting") }
        if (status.value.state == "needsLogin" && status.value.authUrl == null) {
            withContext(commands) { node.login() }
            waitFor { it.authUrl != null || it.state != "needsLogin" }
        }
        status.value.authUrl?.let { return it }
        return when (status.value.state) {
            "running", "needsApproval" -> null
            else -> throw TailnetException(status.value.error ?: COULD_NOT_START)
        }
    }

    private suspend fun waitFor(done: (TailnetStatus) -> Boolean) {
        withTimeoutOrNull(WAIT_FOR.toMillis()) { status.first(done) }
            ?: throw TailnetException(NO_ANSWER)
    }

    override suspend fun signOut() {
        if (!node.load()) return
        withContext(commands) { node.logout() }
    }

    private fun start() {
        if (started) return
        if (!node.load()) return publish(TailnetStatus(state = "error", error = UNAVAILABLE))
        if (!stateDir.isDirectory && !stateDir.mkdirs()) {
            return publish(TailnetStatus(state = "error", error = NO_FOLDER))
        }
        started = true
        routing.set(true)
        // Reported before the node starts, so it never looks for networks itself.
        network.start { default ->
            scope.launch(commands) {
                node.networkChanged(default.interfaceName, default.gateway, network.interfaces())
            }
        }
        scope.launch(commands) {
            try {
                node.start(stateDir.path, deviceName)
            } catch (e: TailnetException) {
                publish(TailnetStatus(state = "error", error = e.message))
            }
        }
        watch()
    }

    private fun stop() {
        if (!started) {
            // A node that never started (no native node here) just goes back to off.
            if (status.value.state == "error") publish(TailnetStatus())
            return
        }
        started = false
        routing.set(false)
        network.stop()
        scope.launch(commands) { node.stop() }
    }

    /**
     * Fresh connections for a node that sat frozen. The node reports itself
     * starting throughout, so a connection made meanwhile waits for it.
     */
    private fun restart() {
        scope.launch(commands) {
            try {
                node.restart(stateDir.path, deviceName)
            } catch (e: TailnetException) {
                publish(TailnetStatus(state = "error", error = e.message))
            }
        }
    }

    /** One long-lived thread parked in the node's status wait, publishing each change. */
    private fun watch() {
        if (watcher != null) return
        watcher = thread(name = "lpm-tailnet-status", isDaemon = true) {
            var version = 0L
            while (true) {
                val (next, nextVersion) = parseStatus(node.waitStatus(version, WAIT_STATUS_MS))
                    ?: continue
                version = nextVersion
                routing.set(next.state == "running" || next.state == "starting")
                publish(next)
            }
        }
    }

    private fun publish(next: TailnetStatus) {
        mutableStatus.value = next
        mutableState.value = next.state
    }

    /**
     * Where to dial `host:port`: a fresh loopback bridge when it's a tailnet address
     * and the node is up, else the address itself — a phone without the built-in
     * node keeps reaching it through the Tailscale app.
     */
    override fun route(host: String, port: Int): Pair<String, Int> {
        if (!routing.get() || !isTailnetHost(host)) return host to port
        val local = node.bridge(host, port)
        return if (local > 0) LOOPBACK to local else host to port
    }

    companion object {
        const val UNAVAILABLE = "Built-in Tailscale isn't available in lpm Link for Android yet."
        const val COULD_NOT_START = "Tailscale couldn't start. Try again in a moment."
        const val NO_ANSWER =
            "Tailscale didn't answer in time. Check your connection and try again."
        const val NO_FOLDER = "Couldn't create a folder for Tailscale's keys."
        private const val LOOPBACK = "127.0.0.1"
        private const val WAIT_STATUS_MS = 30_000
        private val WAIT_FOR: Duration = Duration.ofSeconds(20)
        private val SUSPENDED_LONG_ENOUGH: Duration = Duration.ofSeconds(20)
        private val HEARTBEAT: Duration = Duration.ofSeconds(5)

        /** 100.64.0.0/10 or a MagicDNS name. */
        fun isTailnetHost(host: String): Boolean {
            val octets = host.split('.').map { it.toIntOrNull() }
            if (octets.size == 4 && octets.none { it == null }) {
                return octets[0] == 100 && (octets[1] ?: 0) in 64..127
            }
            return host.endsWith(".ts.net") || host.endsWith(".ts.net.")
        }

        /** The node's status JSON and its version, or null for anything else. */
        internal fun parseStatus(raw: String): Pair<TailnetStatus, Long>? {
            val json = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
                ?: return null
            fun field(name: String) = json[name]?.jsonPrimitive?.takeIf { it.isString }?.content
            val status = TailnetStatus(
                state = field("state") ?: return null,
                authUrl = field("authURL"),
                ip = field("ip"),
                dnsName = field("dnsName"),
                account = field("account"),
                tailnet = field("tailnet"),
                error = field("error")
            )
            return status to (json["version"]?.jsonPrimitive?.longOrNull ?: 0L)
        }
    }
}
