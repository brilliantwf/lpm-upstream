package cx.lpm.mobile.core.push

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import cx.lpm.mobile.core.data.mac.CredentialStore
import cx.lpm.mobile.core.data.push.DeliveredNote
import cx.lpm.mobile.core.data.push.DeliveredNotes
import cx.lpm.mobile.core.data.push.NotificationOpenTarget
import cx.lpm.mobile.core.data.push.PushAlert
import cx.lpm.mobile.core.data.push.PushClear
import cx.lpm.mobile.core.data.push.PushCrypto
import cx.lpm.mobile.core.data.push.PushKind
import cx.lpm.mobile.core.data.session.MacSession
import cx.lpm.mobile.core.model.Project
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

/**
 * Turns an lpm push into a notification, the job iOS splits between its
 * notification service extension and app delegate: the Mac sends only a sealed
 * blob, which is opened here with the phone's push key and posted on the channel
 * of its kind, or — for a withdrawal — matched against what is still showing.
 */
@Singleton
class PushNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val credentials: CredentialStore,
    private val session: MacSession
) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        manager.createNotificationChannelGroupsCompat(
            listOf(
                NotificationChannelGroupCompat.Builder(GROUP_AGENTS)
                    .setName(context.getString(R.string.push_group_agents))
                    .build(),
                NotificationChannelGroupCompat.Builder(GROUP_AUTOMATIONS)
                    .setName(context.getString(R.string.push_group_automations))
                    .build()
            )
        )
        manager.createNotificationChannelsCompat(
            PushKind.entries.map { kind ->
                NotificationChannelCompat.Builder(kind.channelId, kind.importance)
                    .setName(context.getString(kind.channelName))
                    .setGroup(if (kind.isAutomation) GROUP_AUTOMATIONS else GROUP_AGENTS)
                    .build()
            }
        )
    }

    /** One FCM data message, on the messaging service's worker thread. */
    fun handle(data: Map<String, String>) {
        val blob = data[BLOB] ?: return
        // A push can start the process before the app has loaded its stored keys.
        if (!credentials.isLoaded) runBlocking { credentials.load() }
        val key = credentials.loadPushKey() ?: return
        val payload = PushCrypto.open(blob, key) ?: return
        if (data[TYPE] == "background") {
            PushClear.parse(payload)?.let(::withdraw)
            return
        }
        val alert = PushAlert.parse(payload) ?: return
        // The open app shows its live Mac's statuses itself; a push from another
        // Mac, or from this one while the link is down, is news only it carries.
        if (onScreen() && session.showsLive(alert.serverId)) return
        post(alert, data[COLLAPSE_ID])
    }

    /** Withdraw the active Mac's notifications whose news is over. */
    fun reconcile(projects: List<Project>, activeServerId: String?) {
        DeliveredNotes.stale(delivered(), projects, activeServerId).forEach(::cancel)
    }

    private fun withdraw(clear: PushClear) {
        DeliveredNotes.cleared(delivered(), clear).forEach(::cancel)
    }

    private fun post(alert: PushAlert, collapseId: String?) {
        if (!mayPost()) return
        // A later status of the same pane replaces this one, like `apns-collapse-id`.
        val tag = collapseId ?: "${alert.serverId.orEmpty()}|${alert.project}|${alert.statusKey}"
        val extras = Bundle().apply {
            putString(NotificationOpenTarget.PROJECT, alert.project)
            putString(NotificationOpenTarget.TARGET, alert.target)
            putString(NotificationOpenTarget.TERMINAL_ID, alert.terminalId)
            putString(NotificationOpenTarget.AUTOMATION_ID, alert.automationId)
            putString(NotificationOpenTarget.SERVER_ID, alert.serverId)
            putString(STATUS_KEY, alert.statusKey)
            putString(STATUS, alert.status.ifEmpty { null })
        }
        // The app's own launcher activity, which reads these extras.
        val open = Intent(ACTION_OPEN)
            .setPackage(context.packageName)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            .putExtras(extras)
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.component
            ?.let(open::setComponent)
        val pending = PendingIntent.getActivity(
            context,
            tag.hashCode(),
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, alert.kind.channelId)
            .setSmallIcon(R.drawable.ic_stat_lpm)
            .setContentTitle(alert.title)
            .setContentText(alert.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
            .setPriority(
                if (alert.kind.importance == NotificationManager.IMPORTANCE_HIGH) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                }
            )
            .setAutoCancel(true)
            .setContentIntent(pending)
            .addExtras(extras)
            .build()
        try {
            manager.notify(tag, NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // The permission was revoked between the check and the post.
        }
    }

    private fun mayPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return manager.areNotificationsEnabled()
    }

    private fun delivered(): List<DeliveredNote> = manager.activeNotifications.map { sbn ->
        val extras = sbn.notification.extras
        DeliveredNote(
            tag = sbn.tag,
            id = sbn.id,
            project = extras.getString(NotificationOpenTarget.PROJECT),
            statusKey = extras.getString(STATUS_KEY),
            status = extras.getString(STATUS),
            serverId = extras.getString(NotificationOpenTarget.SERVER_ID)
        )
    }

    private fun cancel(note: DeliveredNote) = manager.cancel(note.tag, note.id)

    private fun onScreen(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    companion object {
        /** The action a notification tap opens the app with. */
        const val ACTION_OPEN = "cx.lpm.mobile.action.OPEN_NOTIFICATION"

        private const val BLOB = "blob"
        private const val TYPE = "type"
        private const val COLLAPSE_ID = "collapseId"
        private const val STATUS_KEY = "statusKey"
        private const val STATUS = "status"
        private const val NOTIFICATION_ID = 1
        private const val GROUP_AGENTS = "agents"
        private const val GROUP_AUTOMATIONS = "automations"
    }
}

private val PushKind.isAutomation: Boolean
    get() = this == PushKind.AutomationStarted || this == PushKind.AutomationDone ||
        this == PushKind.AutomationError

private val PushKind.channelId: String
    get() = when (this) {
        PushKind.Waiting -> "agent-waiting"
        PushKind.Done -> "agent-done"
        PushKind.Error -> "agent-error"
        PushKind.AutomationStarted -> "automation-started"
        PushKind.AutomationDone -> "automation-done"
        PushKind.AutomationError -> "automation-error"
    }

private val PushKind.channelName: Int
    get() = when (this) {
        PushKind.Waiting -> R.string.push_channel_waiting
        PushKind.Done -> R.string.push_channel_done
        PushKind.Error -> R.string.push_channel_error
        PushKind.AutomationStarted -> R.string.push_channel_automation_started
        PushKind.AutomationDone -> R.string.push_channel_automation_done
        PushKind.AutomationError -> R.string.push_channel_automation_error
    }

// What needs the user right now pops up; the rest arrives quietly in the shade.
private val PushKind.importance: Int
    get() = when (this) {
        PushKind.Waiting, PushKind.Done, PushKind.Error, PushKind.AutomationError ->
            NotificationManager.IMPORTANCE_HIGH

        PushKind.AutomationStarted, PushKind.AutomationDone ->
            NotificationManager.IMPORTANCE_DEFAULT
    }
