package cx.lpm.mobile.core.tailnet

import javax.inject.Inject
import javax.inject.Singleton
import lpmtailnet.Lpmtailnet

/**
 * The Go node behind Built-in Tailscale (`tailnet/android` at the repo root, bound
 * with gomobile), the role liblpmtailnet.a plays on iOS. Every call blocks; a
 * failure is a [TailnetException] with the node's own message.
 */
interface TailnetNode {
    /** Loads the native node; false on a phone it isn't built for (32-bit ABIs). */
    fun load(): Boolean

    fun start(dir: String, hostname: String)

    fun stop()

    fun restart(dir: String, hostname: String)

    fun login()

    fun logout()

    /** The status as JSON with its version once it moves past [since], or after [timeoutMs]. */
    fun waitStatus(since: Long, timeoutMs: Int): String

    /** A loopback port that reaches `host:port` across the tailnet for 30 seconds, or -1. */
    fun bridge(host: String, port: Int): Int

    /** The default network's interface and IPv4 gateway, and every interface ([NetworkReporter]). */
    fun networkChanged(defaultInterface: String, gateway: String, interfaces: String)
}

@Singleton
class GoTailnetNode @Inject constructor() : TailnetNode {
    // gomobile's glue loads libgojni.so when the class first initializes; the
    // library is missing on ABIs it isn't built for.
    private val loaded by lazy {
        try {
            Lpmtailnet.touch()
            true
        } catch (_: LinkageError) {
            false
        }
    }

    override fun load(): Boolean = loaded

    override fun start(dir: String, hostname: String) = call { Lpmtailnet.start(dir, hostname) }

    override fun stop() = Lpmtailnet.stop()

    override fun restart(dir: String, hostname: String) = call { Lpmtailnet.restart(dir, hostname) }

    override fun login() = call { Lpmtailnet.login() }

    override fun logout() = call { Lpmtailnet.logout() }

    override fun waitStatus(since: Long, timeoutMs: Int): String =
        Lpmtailnet.waitStatus(since, timeoutMs).orEmpty()

    override fun bridge(host: String, port: Int): Int = Lpmtailnet.bridge(host, port)

    override fun networkChanged(defaultInterface: String, gateway: String, interfaces: String) =
        Lpmtailnet.networkChanged(defaultInterface, gateway, interfaces)

    // gomobile declares every failing call as throwing java.lang.Exception.
    private inline fun call(body: () -> Unit) {
        try {
            body()
        } catch (e: Exception) {
            throw TailnetException(e.message ?: DefaultBuiltInTailscale.COULD_NOT_START)
        }
    }
}
