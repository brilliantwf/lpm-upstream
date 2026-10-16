package cx.lpm.mobile.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeAccountsTest {
    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    private val reply = ClaudeAccounts.parse(
        obj(
            """
            {"t": "claudeAccounts", "ok": true,
             "accounts": [
               {"id": "default", "label": "Mine", "signedIn": true, "email": "me@x.com", "plan": "max", "confirmed": true},
               {"id": "work", "label": "Work", "signedIn": true, "email": "me@corp.com", "plan": "team"},
               {"id": "old", "label": "", "signedIn": false, "email": ""},
               {"id": "side", "signedIn": true, "email": "side@x.com", "plan": "pro"}
             ],
             "switching": {"enabled": true, "active": true, "paused": "hold", "allowed": ["work"],
                           "pools": [{"project": null, "current": "side", "members": ["default", "side"]},
                                     {"project": "api", "current": "work"}]}}
            """
        )
    )

    @Test
    fun accountLabelsFollowTheMac() {
        val (main, work, old, side) = reply.accounts
        assertEquals("Main login", main.label)
        assertEquals("Mine", main.name)
        assertEquals("Work", work.label)
        assertEquals("Removed account", old.label)
        assertNull(old.name)
        assertEquals("side@x.com", side.label)
        assertTrue(side.personal)
        assertFalse(work.personal)
    }

    @Test
    fun switchingPoolsAreKeyedByProject() {
        val s = reply.switching
        assertEquals(3, s.maxMembers)
        assertEquals(mapOf("" to "side", "api" to "work"), s.current)
        assertEquals(mapOf("" to listOf("default", "side")), s.members)
        assertEquals("hold", s.paused)
    }

    @Test
    fun derivedText() {
        assertTrue(reply.hasAdded)
        assertEquals("Main login", reply.label(""))
        assertEquals("Removed account", reply.label("gone"))
        assertEquals("Paused · side@x.com", reply.mainNow)
        assertEquals("Sign in on your Mac first", reply.blocked(reply.accounts[2]))
        assertNull(reply.blocked(reply.accounts[1]))
        assertNull(reply.blocked(reply.accounts[3]))
        val unallowed = reply.accounts[1].copy(id = "corp")
        assertEquals("Allow it in lpm on your Mac first", reply.blocked(unallowed))
        assertEquals(
            "Work",
            reply.describe(ClaudeOwnChoice(account = "work", accounts = listOf("side")))
        )
        assertEquals(
            "Work, side@x.com",
            reply.describe(ClaudeOwnChoice(accounts = listOf("work", "side")))
        )
        assertEquals("Main accounts", reply.describe(ClaudeOwnChoice()))
    }

    @Test
    fun mainNowWithoutActiveSwitching() {
        val idle = ClaudeAccounts.parse(obj("""{"accounts": [{"id": "default"}]}"""))
        assertFalse(idle.hasAdded)
        assertEquals("Main login", idle.mainNow)
    }

    @Test
    fun ownChoiceRules() {
        assertTrue(ClaudeOwnChoice().usesMainAccounts(isCopy = false))
        assertFalse(ClaudeOwnChoice().usesMainAccounts(isCopy = true))
        assertTrue(ClaudeOwnChoice(accounts = emptyList()).usesMainAccounts(isCopy = true))
        assertFalse(ClaudeOwnChoice(account = "").usesMainAccounts(isCopy = false))
        assertEquals(
            emptyList<String>(),
            ClaudeOwnChoice(account = "x", accounts = listOf("y")).list
        )
    }

    @Test
    fun choiceWireRoundTrips() {
        val choices = listOf(
            ClaudeAccountChoice.Parent,
            ClaudeAccountChoice.Main,
            ClaudeAccountChoice.Pin(""),
            ClaudeAccountChoice.AccountList(listOf("a", "b"))
        )
        for (choice in choices) assertEquals(choice, ClaudeAccountChoice.fromWire(choice.wire))
        assertEquals(obj("""{"kind":"list","ids":["a","b"]}"""), choices[3].wire)
        assertEquals(obj("""{"kind":"pin","id":""}"""), choices[2].wire)
        assertNull(ClaudeAccountChoice.fromWire(obj("""{"kind":"other"}""")))
    }

    @Test
    fun choiceOwnKeys() {
        assertEquals(ClaudeOwnChoice(), ClaudeAccountChoice.Parent.own(isCopy = true))
        assertEquals(
            ClaudeOwnChoice(accounts = emptyList()),
            ClaudeAccountChoice.Main.own(isCopy = true)
        )
        assertEquals(ClaudeOwnChoice(), ClaudeAccountChoice.Main.own(isCopy = false))
        assertEquals(
            ClaudeOwnChoice(account = "a"),
            ClaudeAccountChoice.Pin("a").own(isCopy = false)
        )
    }
}
