package cx.lpm.mobile.feature.usage.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.session.MacSessions
import cx.lpm.mobile.core.data.usage.StatsState
import cx.lpm.mobile.core.data.usage.StatsStore
import cx.lpm.mobile.core.model.AgentStats
import cx.lpm.mobile.core.navigation.StatsKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the Stats screen shows under its period picker. */
sealed interface StatsBody {
    data object Loading : StatsBody

    data class Failed(val message: String) : StatsBody

    /** A reload in flight dims the numbers already on screen rather than blanking them. */
    data class Loaded(val stats: AgentStats, val dimmed: Boolean) : StatsBody
}

data class StatsUiState(
    val days: Int = 30,
    val body: StatsBody = StatsBody.Loading,
    /** When the body was built, for "2 hours ago" in Recent sessions. */
    val now: Long = 0
)

/** The Stats screen (iOS `StatsScreen`): the Mac's local agent token usage by period. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = StatsViewModel.Factory::class)
class StatsViewModel @AssistedInject constructor(
    @Assisted val key: StatsKey,
    private val sessions: MacSessions,
    private val clock: Clock
) : ViewModel() {
    private val store: StatsStore? get() = sessions[key.session]?.store()

    val uiState: StateFlow<StatsUiState> = sessions.session(key.session)
        .flatMapLatest { s ->
            s?.store<StatsStore>()?.state?.map(::uiState)
                ?: flowOf(StatsUiState())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    private fun uiState(s: StatsState): StatsUiState {
        val stats = s.stats
        val error = s.error
        val body = when {
            stats != null -> StatsBody.Loaded(stats, dimmed = s.loading)
            s.loading -> StatsBody.Loading
            error != null -> StatsBody.Failed(error)
            else -> StatsBody.Loading
        }
        return StatsUiState(days = s.days, body = body, now = clock.millis())
    }

    /** The screen came up: load the period it was left on. */
    fun open() {
        store?.let { it.load(it.state.value.days) }
    }

    fun select(days: Int) {
        store?.load(days)
    }

    fun retry() {
        store?.let { it.load(it.state.value.days) }
    }

    suspend fun refresh() {
        store?.refresh()
    }

    fun close() {
        store?.screenDidClose()
    }

    @AssistedFactory
    interface Factory {
        fun create(key: StatsKey): StatsViewModel
    }
}
