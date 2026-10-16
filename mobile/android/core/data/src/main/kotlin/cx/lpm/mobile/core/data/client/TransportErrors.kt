package cx.lpm.mobile.core.data.client

import java.net.ConnectException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException

/** How a transport failure reads to the user, and its cause chain for Details. */
internal object TransportErrors {
    /**
     * A failed TLS handshake and a refused port need different user action than
     * an unreachable Mac; anything else (or nothing) is the generic offline hint, so
     * a stale classification never outlives the failure that produced it.
     */
    fun classify(error: Throwable?): String {
        val causes = causes(error)
        return when {
            causes.any { it is SSLException || it is CertificateException } ->
                LpmClient.SECURE_FAILED_ERROR

            causes.any {
                it is ConnectException && it.message.orEmpty().contains("refused", true)
            } ->
                LpmClient.REFUSED_ERROR

            else -> LpmClient.OFFLINE_HINT
        }
    }

    /** e.g. "ConnectException/ConnectException": the deepest names the exact failure. */
    fun chain(error: Throwable?): String? =
        causes(error).joinToString("/") { it.javaClass.simpleName }.ifEmpty { null }

    private fun causes(error: Throwable?): List<Throwable> =
        generateSequence(error) { it.cause?.takeIf { cause -> cause !== it } }.take(8).toList()
}
