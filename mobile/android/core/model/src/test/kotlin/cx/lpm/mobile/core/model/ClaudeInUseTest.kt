package cx.lpm.mobile.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeInUseTest {
    private fun project(json: String) = Project.parse(Json.parseToJsonElement(json).jsonObject)

    private fun accounts(active: Boolean = true) = ClaudeAccounts.parse(
        Json.parseToJsonElement(
            """
            {"accounts": [{"id": "default"}, {"id": "work", "label": "Work"}, {"id": "side"}],
             "switching": {"active": $active,
                           "pools": [{"current": "default", "members": ["default", "work"]},
                                     {"project": "api", "current": "side", "members": ["side", "work"]}]}}
            """
        ).jsonObject
    )

    @Test
    fun `the projects list speaks for the main accounts`() {
        val inUse = accounts().inUse(null, null)!!
        assertEquals("default", inUse.current)
        assertEquals(listOf("work"), inUse.standby)
        assertFalse(inUse.pinned)
        assertEquals(
            "New Claude sessions on the main accounts use this account",
            inUse.note("default")
        )
        assertEquals("The main accounts can switch to this account", inUse.note("work"))
        assertNull(inUse.note("side"))
    }

    @Test
    fun `a project's own list is its own pool`() {
        val api = project("""{"name": "api", "claudeAccounts": ["side", "work"]}""")
        val inUse = accounts().inUse(api, null)!!
        assertEquals("side", inUse.current)
        assertEquals(listOf("work"), inUse.standby)
        assertEquals("New Claude sessions in api use this account", inUse.note("side"))
        assertEquals("api can switch to this account", inUse.note("work"))
    }

    @Test
    fun `a pin outranks every list, and an unknown pin is the main login`() {
        val pinned = project("""{"name": "web", "claudeAccount": "work"}""")
        assertEquals(
            ClaudeInUse("work", emptyList(), pinned, pinned = true),
            accounts().inUse(pinned, null)
        )
        val gone = project("""{"name": "web", "claudeAccount": "removed"}""")
        assertEquals("default", accounts().inUse(gone, null)?.current)
    }

    @Test
    fun `a copy follows its parent, and an SSH project has no accounts here`() {
        val parent = project("""{"name": "web", "claudeAccount": "side"}""")
        val copy = project("""{"name": "web-2", "parentName": "web"}""")
        val inUse = accounts().inUse(copy, parent)!!
        assertEquals("side", inUse.current)
        assertTrue(inUse.pinned)
        assertEquals(copy, inUse.project)
        assertNull(accounts().inUse(project("""{"name": "far", "isRemote": true}"""), null))
    }

    @Test
    fun `a list nobody pooled falls back to the main accounts, and paused switching has no pool`() {
        val unknown = project("""{"name": "blog", "claudeAccounts": ["work"]}""")
        assertEquals("default", accounts().inUse(unknown, null)?.current)
        assertEquals(emptyList<String>(), accounts(active = false).inUse(null, null)?.standby)
    }
}
