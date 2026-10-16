package cx.lpm.mobile.core.tailnet

import cx.lpm.mobile.core.data.di.ApplicationScope
import cx.lpm.mobile.core.data.mac.ThisDevice
import cx.lpm.mobile.core.data.prefs.AppPreferences
import cx.lpm.mobile.core.data.prefs.PreferenceKeys
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Built-in Tailscale until the node ships on Android: the switch is remembered as
 * on iOS, and switched on it reports the error iOS shows when its node can't start.
 * Every address is dialed directly, as a phone with the Tailscale app does.
 */
@Singleton
class UnavailableTailscale @Inject constructor(
    private val prefs: AppPreferences,
    device: ThisDevice,
    @ApplicationScope scope: CoroutineScope
) : BuiltInTailscale {
    private val mutableEnabled = MutableStateFlow(false)
    private val mutableStatus = MutableStateFlow(TailnetStatus())
    private val mutableState = MutableStateFlow(mutableStatus.value.state)

    override val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()
    override val status: StateFlow<TailnetStatus> = mutableStatus.asStateFlow()
    override val state: StateFlow<String> = mutableState.asStateFlow()
    override val deviceName: String = "lpm-" + device.noun.lowercase()

    init {
        // The preferences load after this is built; follow them once they do.
        scope.launch {
            prefs.data.collect {
                show(prefs.bool(PreferenceKeys.BUILT_IN_TAILSCALE_ENABLED) ?: false)
            }
        }
    }

    override fun setEnabled(on: Boolean) {
        prefs.setBool(PreferenceKeys.BUILT_IN_TAILSCALE_ENABLED, on)
        show(on)
    }

    override suspend fun signIn(): String? {
        if (!enabled.value) setEnabled(true)
        throw TailnetException(status.value.error ?: UNAVAILABLE)
    }

    override suspend fun signOut() = Unit

    override fun route(host: String, port: Int): Pair<String, Int> = host to port

    private fun show(on: Boolean) {
        mutableEnabled.value = on
        mutableStatus.value =
            if (on) TailnetStatus(state = "error", error = UNAVAILABLE) else TailnetStatus()
        mutableState.value = mutableStatus.value.state
    }

    companion object {
        const val UNAVAILABLE = "Built-in Tailscale isn't available in lpm Link for Android yet."
    }
}
