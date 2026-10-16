package cx.lpm.mobile.core.data.prefs

import cx.lpm.mobile.core.data.testing.InMemoryDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationPrefsTest {
    @Test
    fun `a fresh install wants every push except an automation starting`() = runTest {
        val prefs = AppPreferences(InMemoryDataStore(), backgroundScope)
        assertEquals(
            NotificationPrefs(
                enabled = true,
                waiting = true,
                done = true,
                error = true,
                automationStarted = false,
                automationDone = true,
                automationError = true
            ),
            prefs.notificationPrefs
        )
    }

    @Test
    fun `a change stores every kind under the iOS keys`() = runTest {
        val store = InMemoryDataStore()
        val prefs = AppPreferences(store, backgroundScope)
        prefs.setNotificationPrefs(NotificationPrefs(enabled = false, done = false))
        testScheduler.runCurrent()

        val reloaded = AppPreferences(store, backgroundScope).also { it.load() }
        assertEquals(false, reloaded.bool("lpm.notify.enabled"))
        assertEquals(true, reloaded.bool("lpm.notify.waiting"))
        assertEquals(false, reloaded.bool("lpm.notify.done"))
        assertEquals(true, reloaded.bool("lpm.notify.error"))
        assertEquals(false, reloaded.bool("lpm.notify.automation.started"))
        assertEquals(true, reloaded.bool("lpm.notify.automation.done"))
        assertEquals(true, reloaded.bool("lpm.notify.automation.error"))
        assertEquals(NotificationPrefs(enabled = false, done = false), reloaded.notificationPrefs)
    }

    @Test
    fun `a stored value wins over the default`() = runTest {
        val prefs = AppPreferences(InMemoryDataStore(), backgroundScope)
        prefs.setBool(PreferenceKeys.NOTIFY_AUTOMATION_STARTED, true)
        prefs.setBool(PreferenceKeys.NOTIFY_ERROR, false)
        assertEquals(
            NotificationPrefs(error = false, automationStarted = true),
            prefs.notificationPrefs
        )
    }
}
