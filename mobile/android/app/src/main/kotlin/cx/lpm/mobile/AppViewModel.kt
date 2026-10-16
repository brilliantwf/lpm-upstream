package cx.lpm.mobile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.mobile.core.data.prefs.AppPreferences
import cx.lpm.mobile.core.model.AppearanceMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AppViewModel @Inject constructor(
    private val startup: AppStartup,
    preferences: AppPreferences
) : ViewModel() {
    val ready: StateFlow<Boolean> = startup.ready

    val appearance: StateFlow<AppearanceMode> = preferences.data
        .map { AppearanceMode.of(preferences.string(AppearanceMode.STORAGE_KEY)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceMode.System)

    fun start() = startup.start()
}
