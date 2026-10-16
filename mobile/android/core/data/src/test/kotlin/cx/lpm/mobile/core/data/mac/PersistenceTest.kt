package cx.lpm.mobile.core.data.mac

import cx.lpm.mobile.core.data.client.LpmClient
import cx.lpm.mobile.core.data.prefs.AppPreferences
import cx.lpm.mobile.core.data.prefs.PreferenceKeys
import cx.lpm.mobile.core.data.testing.FakeCipher
import cx.lpm.mobile.core.data.testing.InMemoryDataStore
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistenceTest {
    @Test
    fun `preferences read back what was written, and a wrong type reads as null`() = runTest {
        val store = InMemoryDataStore()
        val prefs = AppPreferences(store, backgroundScope)
        prefs.setBool(PreferenceKeys.NOTIFY_DONE, false)
        prefs.setInt(PreferenceKeys.TERMINAL_FONT_SIZE, 14)
        prefs.setString(PreferenceKeys.TERMINAL_THEME, "claude-dark")
        testScheduler.runCurrent()

        val reloaded = AppPreferences(store, backgroundScope).also { it.load() }
        assertEquals(false, reloaded.bool(PreferenceKeys.NOTIFY_DONE))
        assertEquals(14, reloaded.int(PreferenceKeys.TERMINAL_FONT_SIZE))
        assertEquals("claude-dark", reloaded.string(PreferenceKeys.TERMINAL_THEME))
        assertNull(reloaded.string(PreferenceKeys.TERMINAL_FONT_SIZE))

        reloaded.setString(PreferenceKeys.TERMINAL_FONT_SIZE, "big")
        reloaded.remove(PreferenceKeys.NOTIFY_DONE)
        assertNull(reloaded.int(PreferenceKeys.TERMINAL_FONT_SIZE))
        assertNull(reloaded.bool(PreferenceKeys.NOTIFY_DONE))
    }

    @Test
    fun `saved Macs round-trip with every field`() = runTest {
        val prefs = AppPreferences(InMemoryDataStore(), backgroundScope)
        val store = MacStore(prefs)
        val at = Instant.ofEpochMilli(1_760_000_000_000)
        val records = listOf(
            MacRecord(
                localId = "A",
                serverId = "srv",
                name = "Studio",
                customName = "Desk",
                hosts = listOf("192.168.1.2", "100.64.0.2"),
                port = 8766,
                platform = "macos",
                lastConnected = at,
                farewell = Farewell(Farewell.Reason.Sleep, at)
            ),
            MacRecord(
                localId = "B",
                name = "Box",
                hosts = emptyList(),
                port = 8765,
                via = MacVia("A", "box")
            )
        )
        store.saveRecords(records)
        store.saveActiveId("B")

        assertEquals(records, store.loadRecords())
        assertEquals("B", store.loadActiveId())
        assertTrue(prefs.string(PreferenceKeys.MACS).orEmpty().startsWith("["))
    }

    @Test
    fun `credentials and pins are sealed at rest and survive a reload`() = runTest {
        val disk = InMemoryDataStore()
        val credentials = CredentialStore(disk, FakeCipher(), backgroundScope)
        credentials.save("dev-1", "tok-1", "MAC")
        credentials.savePin("ab12", "MAC")
        val key = credentials.loadOrCreatePushKey()
        testScheduler.runCurrent()

        val names = disk.current.asMap().keys.map { it.name }.toSet()
        assertEquals(setOf("device-credential.MAC", "cert-pin.MAC", "push-key"), names)
        assertTrue(disk.current.asMap().values.none { it.toString().contains("tok-1") })

        val reloaded = CredentialStore(disk, FakeCipher(), backgroundScope).also { it.load() }
        assertEquals(LpmClient.Credential("dev-1", "tok-1"), reloaded.load("MAC"))
        assertEquals("ab12", reloaded.loadPin("MAC"))
        assertTrue(key.contentEquals(reloaded.loadOrCreatePushKey()))

        reloaded.delete("MAC")
        testScheduler.runCurrent()
        assertNull(reloaded.load("MAC"))
        assertNull(reloaded.loadPin("MAC"))
        assertEquals(setOf("push-key"), disk.current.asMap().keys.map { it.name }.toSet())
    }

    @Test
    fun `an address-only name is recognized as such`() {
        val byHost =
            MacRecord(
                localId = "A",
                name = "192.168.1.2",
                hosts = listOf("192.168.1.2"),
                port = 8765
            )
        assertTrue(byHost.isAddressName)
        assertEquals("192.168.1.2", byHost.displayAddress)
        assertTrue(byHost.copy(name = "fe80::1%en0", hosts = emptyList()).isAddressName)
        assertEquals("Desk", byHost.copy(customName = " Desk ").displayName)
        assertTrue(!byHost.copy(customName = "Desk").isAddressName)
    }
}
