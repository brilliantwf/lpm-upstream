package cx.lpm.mobile.core.data.prefs

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Which pushes this phone asks its Macs for (Settings → Notifications). The kinds
 * keep their own values while [enabled] is off; what a Mac is told is
 * `enabled && kind`. Absent keys read as on, so a fresh install opts in to every
 * push that reports something to act on — except an automation starting, which only
 * announces work the user already scheduled.
 */
data class NotificationPrefs(
    val enabled: Boolean = true,
    val waiting: Boolean = true,
    val done: Boolean = true,
    val error: Boolean = true,
    val automationStarted: Boolean = false,
    val automationDone: Boolean = true,
    val automationError: Boolean = true
)

val AppPreferences.notificationPrefs: NotificationPrefs
    get() {
        val defaults = NotificationPrefs()
        return NotificationPrefs(
            enabled = bool(PreferenceKeys.NOTIFY_ENABLED) ?: defaults.enabled,
            waiting = bool(PreferenceKeys.NOTIFY_WAITING) ?: defaults.waiting,
            done = bool(PreferenceKeys.NOTIFY_DONE) ?: defaults.done,
            error = bool(PreferenceKeys.NOTIFY_ERROR) ?: defaults.error,
            automationStarted = bool(PreferenceKeys.NOTIFY_AUTOMATION_STARTED)
                ?: defaults.automationStarted,
            automationDone = bool(PreferenceKeys.NOTIFY_AUTOMATION_DONE) ?: defaults.automationDone,
            automationError = bool(PreferenceKeys.NOTIFY_AUTOMATION_ERROR)
                ?: defaults.automationError
        )
    }

fun AppPreferences.notificationPrefsFlow(): Flow<NotificationPrefs> =
    data.map { notificationPrefs }.distinctUntilChanged()

/** Stores every kind, as iOS does on any change, so all seven keys hold a value after. */
fun AppPreferences.setNotificationPrefs(prefs: NotificationPrefs) {
    setBool(PreferenceKeys.NOTIFY_ENABLED, prefs.enabled)
    setBool(PreferenceKeys.NOTIFY_WAITING, prefs.waiting)
    setBool(PreferenceKeys.NOTIFY_DONE, prefs.done)
    setBool(PreferenceKeys.NOTIFY_ERROR, prefs.error)
    setBool(PreferenceKeys.NOTIFY_AUTOMATION_STARTED, prefs.automationStarted)
    setBool(PreferenceKeys.NOTIFY_AUTOMATION_DONE, prefs.automationDone)
    setBool(PreferenceKeys.NOTIFY_AUTOMATION_ERROR, prefs.automationError)
}
