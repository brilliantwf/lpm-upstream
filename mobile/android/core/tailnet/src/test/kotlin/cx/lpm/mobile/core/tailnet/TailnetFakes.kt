package cx.lpm.mobile.core.tailnet

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.util.Collections
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeStore : DataStore<Preferences> {
    val flow = MutableStateFlow(emptyPreferences())
    override val data: StateFlow<Preferences> = flow

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences
    ): Preferences = transform(flow.value).also { flow.value = it }
}

/**
 * The Go node as the tests script it: every call is recorded, and the statuses it
 * reports are queued with [report] (or on [start]/[login]) and handed to the
 * status watcher in order.
 */
class FakeNode(private val loads: Boolean = true) : TailnetNode {
    val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private val statuses = LinkedBlockingQueue<String>()
    private var version = 0L
    var onStart: List<TailnetStatus> = emptyList()
    var onLogin: List<TailnetStatus> = emptyList()
    var loginFailure: String? = null
    var bridgePort = 41_000

    @Synchronized
    fun report(status: TailnetStatus) {
        version++
        val fields = listOfNotNull(
            "\"state\":\"${status.state}\"",
            status.authUrl?.let { "\"authURL\":\"$it\"" },
            status.error?.let { "\"error\":\"$it\"" },
            "\"version\":$version"
        )
        statuses.put(fields.joinToString(",", "{", "}"))
    }

    override fun load() = loads

    override fun start(dir: String, hostname: String) {
        calls += "start $hostname"
        onStart.forEach(::report)
    }

    override fun stop() {
        calls += "stop"
    }

    override fun restart(dir: String, hostname: String) {
        calls += "restart"
    }

    override fun login() {
        calls += "login"
        loginFailure?.let { throw TailnetException(it) }
        onLogin.forEach(::report)
    }

    override fun logout() {
        calls += "logout"
    }

    override fun waitStatus(since: Long, timeoutMs: Int): String =
        statuses.poll(POLL_MS, TimeUnit.MILLISECONDS).orEmpty()

    override fun bridge(host: String, port: Int): Int {
        calls += "bridge $host:$port"
        return bridgePort
    }

    override fun networkChanged(defaultInterface: String, gateway: String, interfaces: String) {
        calls += "network $defaultInterface $gateway"
    }

    private companion object {
        const val POLL_MS = 20L
    }
}

class FakeNetwork : NetworkReporter {
    var onChange: ((DefaultNetwork) -> Unit)? = null
    var stops = 0

    override fun start(onChange: (DefaultNetwork) -> Unit) {
        this.onChange = onChange
        onChange(DefaultNetwork("wlan0", "192.168.1.1"))
    }

    override fun stop() {
        stops++
        onChange = null
    }

    override fun interfaces() = "wlan0 30 1500 true true false false true | 192.168.1.20/24"
}

/** Starts out on screen, as the process is when the first activity has started. */
class FakeVisibility : AppVisibility {
    private var onForeground: () -> Unit = {}
    private var onBackground: () -> Unit = {}

    override fun observe(onForeground: () -> Unit, onBackground: () -> Unit) {
        this.onForeground = onForeground
        this.onBackground = onBackground
        onForeground()
    }

    fun foreground() = onForeground()

    fun background() = onBackground()
}

/** Waits in real time for the status watcher thread to publish [state]. */
fun BuiltInTailscale.awaitState(state: String) {
    val deadline = System.currentTimeMillis() + 5_000
    while (this.state.value != state) {
        check(System.currentTimeMillis() < deadline) { "never reached $state: ${status.value}" }
        Thread.sleep(5)
    }
}
