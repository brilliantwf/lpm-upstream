package cx.lpm.mobile.core.data.usage

import cx.lpm.mobile.core.data.client.LpmClient
import cx.lpm.mobile.core.data.client.requestStats
import cx.lpm.mobile.core.data.session.GenerationTimeout
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.data.session.SessionStore
import cx.lpm.mobile.core.model.AgentStats
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class StatsState(
    /** Null until the first load lands. */
    val stats: AgentStats? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /** The selected period: 1, 7 or 30 days, 0 = all time. */
    val days: Int = 30
)

/**
 * Local agent token-usage stats (iOS `AppModel` stats, the desktop Stats page). It
 * also hands the usage rows their `days: 1` answers: one `stats` reply serves both,
 * told apart by the period it echoes.
 */
class StatsStore(private val session: MacSession) : SessionStore {
    private val mutable = MutableStateFlow(StatsState())
    val state: StateFlow<StatsState> = mutable.asStateFlow()

    // Re-issues the query after a reconnect while the Stats screen is open.
    private var active = false

    // The scan replies asynchronously and can arrive out of order (the Mac scans
    // history files off the socket), so a superseded timeout must not clear a newer load.
    private val timeout = GenerationTimeout<String>(session.scope)

    private val usage: UsageStore get() = session.store()

    override fun wire(client: LpmClient) {
        client.onStats = ::receive
    }

    override fun reset() {
        timeout.cancelAll()
        active = false
        mutable.value = StatsState()
    }

    /**
     * A stats scan in flight can't survive the reconnect; re-issue it if the screen is
     * still open, otherwise just drop the spinner.
     */
    override fun onConnectionReset() {
        if (active) {
            load(state.value.days)
        } else {
            mutable.update { it.copy(loading = false) }
        }
    }

    /**
     * Load a period (1/7/30 days, 0 = all time). Marks the screen active so a reconnect
     * re-issues the query, and dims rather than blanks while reloading.
     */
    fun load(days: Int) {
        active = true
        mutable.update { it.copy(days = days, loading = true, error = null) }
        session.client?.requestStats(days)
        // If the scan never answers (an old Mac that doesn't know this message, or a
        // reply lost while the socket stayed up), give up so the screen shows an error
        // instead of an endless skeleton. A reload keeps its data; only the first,
        // dataless load surfaces the error.
        timeout.arm(STATS, STATS_TIMEOUT_MS) {
            if (!state.value.loading) return@arm
            mutable.update {
                it.copy(
                    loading = false,
                    error = if (it.stats ==
                        null
                    ) {
                        "Couldn't load stats. Pull to refresh."
                    } else {
                        it.error
                    }
                )
            }
        }
    }

    /** Pull-to-refresh: re-request when live, else reconnect. */
    suspend fun refresh() {
        if (session.state.value.isReady) load(state.value.days) else session.reconnectIfNeeded()
        delay(REFRESH_SETTLE_MS)
    }

    /** The Stats screen closed: a straggler reply or a reconnect stops refetching. */
    fun screenDidClose() {
        active = false
    }

    private fun receive(stats: AgentStats?, error: String?) {
        if (stats != null) {
            // The usage rows ask for today alone; the echoed period tells their answer
            // apart from the Stats screen's.
            if (stats.days == 1) usage.applyToday(stats)
            // Only the current period may land; a superseded one is stale.
            if (stats.days != state.value.days) return
            timeout.cancel(STATS)
            mutable.update { it.copy(stats = stats, error = null, loading = false) }
        } else if (state.value.loading) {
            // A failure echoes no period; only a screen waiting on one claims it.
            timeout.cancel(STATS)
            mutable.update { it.copy(loading = false, error = error ?: "Couldn't load stats.") }
        }
    }

    private companion object {
        const val STATS = "stats"
        const val STATS_TIMEOUT_MS = 30_000L
        const val REFRESH_SETTLE_MS = 500L
    }
}
