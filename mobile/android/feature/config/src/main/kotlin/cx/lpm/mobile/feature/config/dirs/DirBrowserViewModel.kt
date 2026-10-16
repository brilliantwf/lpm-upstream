package cx.lpm.mobile.feature.config.dirs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.config.ProjectConfigStore
import cx.lpm.mobile.core.data.session.MacSessions
import cx.lpm.mobile.core.model.DirListing
import cx.lpm.mobile.core.model.SessionKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class DirBrowserUiState(
    val listing: DirListing? = null,
    val loading: Boolean = false,
    val error: String? = null
)

/**
 * The Mac folder browser (iOS `DirBrowserView`): one level at a time from the home
 * folder, for the add-project forms to pick a folder.
 */
@HiltViewModel
class DirBrowserViewModel @Inject constructor(sessions: MacSessions) : ViewModel() {
    private val config: ProjectConfigStore? = sessions[SessionKey.Live]?.store()

    val uiState: StateFlow<DirBrowserUiState> = (config?.state ?: flowOf(null))
        .map { c ->
            DirBrowserUiState(c?.dirListing, c?.dirListingLoading == true, c?.dirListingError)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DirBrowserUiState())

    init {
        config?.loadDirs(HOME)
    }

    fun open(path: String) {
        config?.loadDirs(path)
    }

    fun openChild(name: String) {
        val base = config?.state?.value?.dirListing?.path ?: return
        open(if (base.endsWith("/")) base + name else "$base/$name")
    }

    private companion object {
        const val HOME = "~"
    }
}
