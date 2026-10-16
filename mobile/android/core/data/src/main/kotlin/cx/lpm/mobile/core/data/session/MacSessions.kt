package cx.lpm.mobile.core.data.session

import cx.lpm.mobile.core.model.SessionKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Every session a screen can belong to: the live Mac's, and the machine visit open
 * beside it. Navigation keys carry a [SessionKey], and a screen's ViewModel resolves
 * its stores with `sessions[key]?.store<XStore>()`, so the same screen works inside
 * a visit with no second code path.
 */
@Singleton
class MacSessions @Inject constructor(val live: MacSession) {
    /** `key`'s session, or null for a visit that has ended ("Project unavailable"). */
    operator fun get(key: SessionKey): MacSession? = when (key) {
        SessionKey.Live -> live
        is SessionKey.Visit -> live.visitSession.value?.takeIf { it.key == key }
    }

    /** Follows `key`'s session as visits open and end. */
    fun session(key: SessionKey): Flow<MacSession?> = when (key) {
        SessionKey.Live -> flowOf(live)

        is SessionKey.Visit -> live.visitSession.map {
            it?.takeIf { s -> s.key == key }
        }.distinctUntilChanged()
    }

    /** The visit sessions open beside the live one (at most one, as on iOS). */
    val visits: Flow<List<MacSession>> = live.visitSession.map { listOfNotNull(it) }

    /** Every session open now, the live one first. */
    val all: List<MacSession> get() = listOfNotNull(live, live.visitSession.value)
}
