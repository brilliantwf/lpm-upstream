package cx.lpm.mobile.core.tailnet

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import cx.lpm.mobile.core.data.mac.ThisDevice
import cx.lpm.mobile.core.data.prefs.AppPreferences
import cx.lpm.mobile.core.data.prefs.PreferenceKeys
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UnavailableTailscaleTest {
    private class Store : DataStore<Preferences> {
        val flow = MutableStateFlow(emptyPreferences())
        override val data: StateFlow<Preferences> = flow

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences
        ): Preferences = transform(flow.value).also { flow.value = it }
    }

    @Test
    fun dialsEveryAddressDirectly() = runTest {
        val tailscale =
            UnavailableTailscale(
                AppPreferences(Store(), backgroundScope),
                ThisDevice("Pixel"),
                backgroundScope
            )
        assertEquals("100.64.1.2" to 8765, tailscale.route("100.64.1.2", 8765))
        assertEquals("lpm-phone", tailscale.deviceName)
    }

    @Test
    fun switchedOnItReportsTheErrorAndRemembersTheSwitch() = runTest {
        val prefs = AppPreferences(Store(), backgroundScope)
        val tailscale = UnavailableTailscale(prefs, ThisDevice("Pixel"), backgroundScope)
        assertFalse(tailscale.enabled.value)
        assertEquals("off", tailscale.state.value)

        tailscale.setEnabled(true)
        assertTrue(tailscale.enabled.value)
        assertEquals("error", tailscale.state.value)
        assertEquals(UnavailableTailscale.UNAVAILABLE, tailscale.status.value.error)
        assertEquals(true, prefs.bool(PreferenceKeys.BUILT_IN_TAILSCALE_ENABLED))

        tailscale.setEnabled(false)
        assertEquals("off", tailscale.state.value)
        assertNull(tailscale.status.value.error)
    }

    @Test
    fun followsTheSavedSwitchOnceThePreferencesLoad() = runTest {
        val store = Store()
        val seeded = AppPreferences(store, backgroundScope)
        seeded.setBool(PreferenceKeys.BUILT_IN_TAILSCALE_ENABLED, true)
        runCurrent()
        val prefs = AppPreferences(store, backgroundScope)
        val tailscale = UnavailableTailscale(prefs, ThisDevice("Pixel"), backgroundScope)
        runCurrent()
        assertFalse(tailscale.enabled.value)
        prefs.load()
        runCurrent()
        assertTrue(tailscale.enabled.value)
    }

    @Test
    fun signInFailsWithTheSameError() = runTest {
        val tailscale =
            UnavailableTailscale(
                AppPreferences(Store(), backgroundScope),
                ThisDevice("Pixel"),
                backgroundScope
            )
        val error = runCatching { tailscale.signIn() }.exceptionOrNull()
        assertTrue(error is TailnetException)
        assertEquals(UnavailableTailscale.UNAVAILABLE, error?.message)
        assertTrue(tailscale.enabled.value)
    }
}
