package cx.lpm.mobile.feature.actions.run

import cx.lpm.mobile.core.model.Action
import cx.lpm.mobile.core.model.SessionKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * The action the ⚡ sheet picked, held until the screen under it runs it once the
 * sheet is gone (iOS `ProjectActionsSheetHost`): running it may open a sheet of its own.
 */
@Singleton
class ActionPicks @Inject constructor() {
    private val picked = MutableStateFlow<Map<Pair<SessionKey, String>, Action>>(emptyMap())

    fun pick(session: SessionKey, project: String, action: Action) =
        picked.update { it + ((session to project) to action) }

    fun pending(session: SessionKey, project: String): Flow<Action?> =
        picked.map { it[session to project] }.distinctUntilChanged()

    fun take(session: SessionKey, project: String): Action? {
        val action = picked.value[session to project] ?: return null
        picked.update { it - (session to project) }
        return action
    }
}
