package cx.lpm.mobile.core.push

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import cx.lpm.mobile.core.model.Project
import cx.lpm.mobile.core.testing.TestSession
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PushNotifierTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val waiting =
        """{"serverId":"srv-1","project":"api","projectLabel":"API","target":"terminal",""" +
            """"terminal":"Claude","terminalId":"t-1","status":"Waiting","ts":1,"key":"k1"}"""

    @Before
    fun allowNotifications() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun seal(plain: String, key: ByteArray): String {
        val nonce = ByteArray(12) { it.toByte() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        return Base64.getEncoder().encodeToString(nonce + cipher.doFinal(plain.encodeToByteArray()))
    }

    private suspend fun TestScope.notifier(): Pair<PushNotifier, TestSession> {
        val t = TestSession(this)
        t.credentials.load()
        t.credentials.loadOrCreatePushKey()
        val notifier = PushNotifier(app, t.credentials, t.session)
        notifier.createChannels()
        return notifier to t
    }

    private fun shown(): List<Notification> = manager.activeNotifications.map { it.notification }

    @Test
    fun anAlertIsPostedOnItsKindsChannelAndOpensItsTerminal() = runTest {
        val (notifier, t) = notifier()
        notifier.handle(
            mapOf("blob" to seal(waiting, t.credentials.loadPushKey()!!), "collapseId" to "c1")
        )

        val sbn = manager.activeNotifications.single()
        val n = sbn.notification
        assertEquals("c1", sbn.tag)
        assertEquals("agent-waiting", n.channelId)
        assertEquals("API", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(
            "Claude — Agent is waiting for you",
            n.extras.getString(Notification.EXTRA_TEXT)
        )
        val open = shadowOf(n.contentIntent).savedIntent
        assertEquals(PushNotifier.ACTION_OPEN, open.action)
        assertEquals("api", open.getStringExtra("project"))
        assertEquals("terminal", open.getStringExtra("target"))
        assertEquals("t-1", open.getStringExtra("terminalId"))
        assertEquals("srv-1", open.getStringExtra("serverId"))
        val channel = manager.getNotificationChannel("agent-waiting")
        assertEquals("agents", channel.group)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertEquals("automations", manager.getNotificationChannel("automation-done").group)
    }

    @Test
    fun aWithdrawalTakesTheNotificationBack() = runTest {
        val (notifier, t) = notifier()
        val key = t.credentials.loadPushKey()!!
        notifier.handle(mapOf("blob" to seal(waiting, key), "collapseId" to "c1"))
        notifier.handle(
            mapOf(
                "blob" to
                    seal("""{"serverId":"srv-2","clear":[{"project":"api","key":"k1"}]}""", key),
                "type" to "background"
            )
        )
        assertEquals(1, shown().size)
        notifier.handle(
            mapOf(
                "blob" to
                    seal("""{"serverId":"srv-1","clear":[{"project":"api","key":"k1"}]}""", key),
                "type" to "background"
            )
        )
        assertTrue(shown().isEmpty())
    }

    @Test
    fun aStatusThatMovedOnIsWithdrawnWhenTheListArrives() = runTest {
        val (notifier, t) = notifier()
        notifier.handle(mapOf("blob" to seal(waiting, t.credentials.loadPushKey()!!)))
        val running = Project.parse(
            Json.parseToJsonElement(
                """{"name":"api","statusEntries":[{"key":"k1","value":"Running"}]}"""
            ) as JsonObject
        )
        // A project the list doesn't carry is left alone.
        notifier.reconcile(emptyList(), "srv-1")
        assertEquals(1, shown().size)
        notifier.reconcile(listOf(running), "srv-1")
        assertTrue(shown().isEmpty())
    }

    @Test
    fun theOpenAppLeavesItsLiveMacsNewsToTheScreen() = runTest {
        val (notifier, t) = notifier()
        t.connectReady()
        (ProcessLifecycleOwner.get().lifecycle as LifecycleRegistry).currentState =
            Lifecycle.State.RESUMED
        try {
            val key = t.credentials.loadPushKey()!!
            notifier.handle(mapOf("blob" to seal(waiting, key)))
            assertTrue(shown().isEmpty())
            // Another Mac's news is shown even with the app open.
            notifier.handle(mapOf("blob" to seal(waiting.replace("srv-1", "srv-2"), key)))
            assertEquals(1, shown().size)
        } finally {
            (ProcessLifecycleOwner.get().lifecycle as LifecycleRegistry).currentState =
                Lifecycle.State.CREATED
        }
    }

    @Test
    fun garbageAndForeignBlobsShowNothing() = runTest {
        val (notifier, _) = notifier()
        notifier.handle(mapOf("blob" to "not a blob"))
        notifier.handle(mapOf("blob" to seal(waiting, ByteArray(32))))
        notifier.handle(emptyMap())
        assertTrue(shown().isEmpty())
    }
}
