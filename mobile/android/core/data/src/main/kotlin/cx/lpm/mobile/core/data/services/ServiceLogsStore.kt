package cx.lpm.mobile.core.data.services

import cx.lpm.mobile.core.data.client.LpmClient
import cx.lpm.mobile.core.data.client.requestServiceLogs
import cx.lpm.mobile.core.data.client.setServiceLogsWatch
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.data.session.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ServiceLogsState(
    /** [ServiceLogsStore.key] → the service's recent output. */
    val results: Map<String, String> = emptyMap(),
    /** [ServiceLogsStore.key] → why the output couldn't be read. */
    val errors: Map<String, String> = emptyMap()
)

/**
 * A running service's recent output, by project and pane: the logs sheet's text
 * and the composer's "<service> logs" mention. A Mac that pushes a watched
 * service's output as it changes keeps it current; an older one answers polls.
 */
class ServiceLogsStore(private val session: MacSession) : SessionStore {
    private val mutable = MutableStateFlow(ServiceLogsState())
    val state: StateFlow<ServiceLogsState> = mutable.asStateFlow()

    private val client: LpmClient? get() = session.client

    /** The Mac pushes a watched service's output; an older one only answers `serviceLogs`. */
    val canWatch: Boolean
        get() = client?.hostFeatures?.contains(LpmClient.SERVICE_LOGS_WATCH_FEATURE) == true

    override fun wire(client: LpmClient) {
        client.onServiceLogs = { project, paneIndex, text, error ->
            val key = key(project, paneIndex)
            mutable.update {
                if (text != null) {
                    it.copy(results = it.results + (key to text), errors = it.errors - key)
                } else {
                    it.copy(errors = it.errors + (key to (error ?: "Couldn't read the logs.")))
                }
            }
        }
    }

    override fun reset() {
        mutable.value = ServiceLogsState()
    }

    fun key(project: String, paneIndex: Int): String = project + "\n" + paneIndex

    fun fetch(project: String, paneIndex: Int, lines: Int = DEFAULT_LINES) {
        client?.requestServiceLogs(project, paneIndex, lines)
    }

    fun watch(project: String, paneIndex: Int, watch: Boolean) {
        client?.setServiceLogsWatch(project, paneIndex, watch)
    }

    /** Done with a reply: forget it, so the next one is news. */
    fun consume(key: String) = mutable.update {
        it.copy(
            results = it.results - key,
            errors =
                it.errors - key
        )
    }

    private companion object {
        const val DEFAULT_LINES = 200
    }
}
