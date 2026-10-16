package cx.lpm.mobile.core.data.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import cx.lpm.mobile.core.data.client.Socket
import cx.lpm.mobile.core.data.client.SocketListener
import cx.lpm.mobile.core.data.client.SocketTransport
import cx.lpm.mobile.core.data.link.HostProber
import cx.lpm.mobile.core.data.link.MacBrowser
import cx.lpm.mobile.core.data.link.NetworkMonitor
import cx.lpm.mobile.core.data.mac.SecretCipher
import cx.lpm.mobile.core.model.string
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Wall time that follows the test scheduler's virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class SchedulerClock(
    private val scheduler: TestCoroutineScheduler,
    private val start: Instant = Instant.parse("2026-10-15T12:00:00Z")
) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = start.plusMillis(scheduler.currentTime)
}

class FakeTransport : SocketTransport {
    val sockets = mutableListOf<FakeSocket>()
    val last: FakeSocket get() = sockets.last()

    override fun open(url: String, trust: X509TrustManager, listener: SocketListener): Socket =
        FakeSocket(url, trust, listener).also { sockets += it }
}

class FakeSocket(
    val url: String,
    val trust: X509TrustManager,
    private val listener: SocketListener
) : Socket {
    val sent = mutableListOf<String>()
    var closed = false
        private set

    override fun send(text: String): Boolean {
        if (closed) return false
        sent += text
        return true
    }

    override fun close() {
        closed = true
    }

    fun open() = listener.onOpen(this)

    fun receive(text: String) = listener.onMessage(this, text)

    fun fail(error: Throwable? = IOException("connection reset")) = listener.onFailure(this, error)

    private var answeredPings = 0

    /** The Mac answers every heartbeat sent so far, so the link stays up. */
    fun answerPings() {
        if (closed) return
        repeat(sentOf("ping").size - answeredPings) {
            receive("""{"t":"pong"}""")
            answeredPings++
        }
    }

    /** The `t` of each frame sent so far. */
    val sentTypes: List<String> get() = sent.map { frame(it).string("t").orEmpty() }

    fun sentOf(type: String): List<JsonObject> = sent.map(::frame).filter { it.string("t") == type }

    private fun frame(text: String) = Json.parseToJsonElement(text) as JsonObject
}

class InMemoryDataStore : DataStore<Preferences> {
    private val flow = MutableStateFlow(emptyPreferences())
    override val data: StateFlow<Preferences> = flow

    val current: Preferences get() = flow.value

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences
    ): Preferences {
        val next = transform(flow.value)
        flow.value = next
        return next
    }
}

/** Not secret, but round-trips and fails on tampering like the real one. */
class FakeCipher : SecretCipher {
    override fun seal(plain: ByteArray): ByteArray = MAGIC + plain.reversedArray()

    override fun open(sealed: ByteArray): ByteArray? {
        if (sealed.size < MAGIC.size ||
            !sealed.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)
        ) {
            return null
        }
        return sealed.copyOfRange(MAGIC.size, sealed.size).reversedArray()
    }

    private companion object {
        val MAGIC = byteArrayOf(0x1f, 0x2e)
    }
}

class FakeNetworkMonitor : NetworkMonitor {
    override val network = MutableStateFlow(NetworkMonitor.Network(NetworkMonitor.Medium.Wifi))
    override var onChange: (
        (
            from: NetworkMonitor.Medium,
            to: NetworkMonitor.Medium
        ) -> Unit
    )? = null
    var started = false

    override fun start() {
        started = true
    }
}

class FakeMacBrowser : MacBrowser {
    override val browse = MutableStateFlow(MacBrowser.Browse())
    override var onChange: ((List<MacBrowser.DiscoveredMac>) -> Unit)? = null
    var browsing = false
    val addresses = mutableMapOf<String, MacBrowser.Resolved>()

    override fun start() {
        browsing = true
    }

    override fun stop() {
        browsing = false
    }

    override suspend fun resolve(
        mac: MacBrowser.DiscoveredMac,
        timeoutMs: Long
    ): MacBrowser.Resolved? = addresses[mac.serviceName]
}

/** Answers from a fixed table; unknown hosts don't answer. */
class FakeHostProber(val reachable: MutableSet<String> = mutableSetOf()) : HostProber {
    val probed = mutableListOf<String>()
    val identities = mutableListOf<HostProber.Identity>()

    override suspend fun open(
        host: String,
        port: Int,
        identity: HostProber.Identity,
        timeoutMs: Long
    ): HostProber.Outcome {
        probed += host
        identities += identity
        val ok = host in reachable
        return HostProber.Outcome(host, ok, if (ok) "open" else "refused")
    }
}
