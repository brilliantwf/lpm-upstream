package cx.lpm.mobile.feature.connection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.link.AddressCheck
import cx.lpm.mobile.core.data.link.MacBrowser
import cx.lpm.mobile.core.data.mac.MacRecord
import cx.lpm.mobile.core.data.session.MacSessions
import cx.lpm.mobile.core.model.DEFAULT_MAC_PORT
import cx.lpm.mobile.core.model.SessionKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One editable address; `id` keeps a row's place while the list is reordered. */
data class HostField(val id: Long, val text: String) {
    val host: String get() = text.trim()
}

/** What the address editor reads from the session. */
data class EditEndpointUiState(
    val record: MacRecord? = null,
    /** The address the live link uses, when it's this Mac's and it's up. */
    val inUse: String? = null,
    val testing: Set<String> = emptySet(),
    val checks: Map<String, AddressCheck> = emptyMap()
)

/** Whether "Find on this Wi-Fi" found the Mac. */
sealed interface FindResult {
    data class Found(val host: String) : FindResult

    data object NotFound : FindResult
}

/**
 * The addresses and port the phone uses to reach a saved Mac, edited without
 * re-pairing. Each address can be tested before saving, and the Mac can be looked
 * for on this Wi-Fi. The form is Compose state so text fields edit it directly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = EditEndpointViewModel.Factory::class)
class EditEndpointViewModel @AssistedInject constructor(
    @Assisted private val key: SessionKey,
    @Assisted private val macId: String,
    private val sessions: MacSessions,
    private val browser: MacBrowser
) : ViewModel() {
    val uiState: StateFlow<EditEndpointUiState> = sessions.session(key)
        .flatMapLatest { session ->
            session ?: return@flatMapLatest flowOf(EditEndpointUiState())
            combine(session.state, session.link.state) { s, link ->
                EditEndpointUiState(
                    record = s.macs.firstOrNull { it.localId == macId },
                    inUse = link.host?.takeIf { s.activeMacId == macId && s.isReady },
                    testing = link.testing,
                    checks = link.checks
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditEndpointUiState())

    val hosts = mutableStateListOf<HostField>()
    var port by mutableStateOf("")
    var finding by mutableStateOf(false)
        private set
    var findResult by mutableStateOf<FindResult?>(null)
        private set
    private var nextId = 0L

    private val record: MacRecord? get() = sessions[key]?.state?.value?.macs?.firstOrNull {
        it.localId ==
            macId
    }

    init {
        val existing = record?.hosts.orEmpty()
        (existing.ifEmpty { listOf("") }).forEach { hosts += field(it) }
        port = (record?.port ?: DEFAULT_MAC_PORT).toString()
        test()
    }

    val cleanedHosts: List<String> get() = hosts.map { it.host }.filter { it.isNotEmpty() }

    val portValue: Int? get() = port.toIntOrNull()?.takeIf { it in 1..MAX_PORT }

    val canSave: Boolean
        get() = cleanedHosts.isNotEmpty() && cleanedHosts.all(::isPlausibleHost) &&
            portValue != null

    val canFind: Boolean get() = record?.serverId != null

    fun setHost(id: Long, text: String) {
        val index = hosts.indexOfFirst { it.id == id }
        if (index >= 0) hosts[index] = hosts[index].copy(text = text)
    }

    fun addHost() {
        hosts += field("")
    }

    fun removeHost(id: Long) {
        hosts.removeAll { it.id == id }
    }

    fun moveHost(from: Int, to: Int) {
        if (from !in hosts.indices || to !in hosts.indices || from == to) return
        hosts.add(to, hosts.removeAt(from))
    }

    fun test() {
        val session = sessions[key] ?: return
        val p = portValue ?: return
        val hosts = cleanedHosts
        viewModelScope.launch { session.link.test(hosts, p, updatingIssue = false, mac = record) }
    }

    /** Look for this Mac on the current Wi-Fi and add the address it answers at. */
    fun find() {
        val serverId = record?.serverId ?: return
        finding = true
        findResult = null
        browser.start()
        viewModelScope.launch {
            try {
                repeat(FIND_ATTEMPTS) {
                    val mac = browser.browse.value.found.firstOrNull { it.serverId == serverId }
                    val resolved = mac?.let { browser.resolve(it) }
                    if (resolved != null) {
                        if (resolved.host !in cleanedHosts) hosts.add(0, field(resolved.host))
                        port = resolved.port.toString()
                        findResult = FindResult.Found(resolved.host)
                        test()
                        return@launch
                    }
                    delay(FIND_INTERVAL_MS)
                }
                findResult = FindResult.NotFound
            } finally {
                finding = false
                browser.stop()
            }
        }
    }

    fun save() {
        val p = portValue ?: return
        sessions[key]?.updateEndpoint(macId, cleanedHosts, p)
    }

    override fun onCleared() = browser.stop()

    private fun field(text: String) = HostField(nextId++, text)

    @AssistedFactory
    interface Factory {
        fun create(key: SessionKey, macId: String): EditEndpointViewModel
    }

    companion object {
        private const val MAX_PORT = 65_535
        private const val FIND_ATTEMPTS = 16
        private const val FIND_INTERVAL_MS = 500L

        /** A bare IP address or hostname: no scheme, path or spaces. */
        fun isPlausibleHost(s: String): Boolean = s.isNotEmpty() && ' ' !in s && '/' !in s
    }
}
