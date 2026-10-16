package cx.lpm.mobile.feature.actions.editor

import cx.lpm.mobile.feature.actions.zone
import org.junit.Assert.assertEquals
import org.junit.Test

class ActionPlacementTest {
    private val zones = listOf(
        zone("""{"name":"checks","label":"Checks","source":"project"}"""),
        zone(
            """{"name":"agents","label":"Agents","source":"global",""" +
                """"layers":[{"name":"claude"},{"name":"codex"}]}"""
        )
    )

    @Test
    fun `display and layer read as the desktop places them`() {
        assertEquals(ActionPlacement.Header, ActionPlacement.of("", null, zones))
        assertEquals(ActionPlacement.Header, ActionPlacement.of("button", null, zones))
        assertEquals(ActionPlacement.Footer, ActionPlacement.of("footer", null, zones))
        assertEquals(
            ActionPlacement.InZone("agents", "claude"),
            ActionPlacement.of("agents", "x", zones)
        )
        assertEquals(
            ActionPlacement.InZone("checks", null),
            ActionPlacement.of("checks", null, zones)
        )
        assertEquals(ActionPlacement.InZone("gone", "l"), ActionPlacement.of("gone", "l", zones))
        assertEquals(ActionPlacement.InZone("gone", null), ActionPlacement.of("gone", "", zones))
    }

    @Test
    fun `a wider file's action only goes in zones from files as wide`() {
        val project = ActionPlacement.options(zones, "project", ActionPlacement.Header)
        assertEquals(
            listOf(
                ActionPlacement.Header,
                ActionPlacement.Footer,
                ActionPlacement.InZone("checks", null),
                ActionPlacement.InZone("agents", "claude"),
                ActionPlacement.InZone("agents", "codex")
            ),
            project
        )
        val repo = ActionPlacement.options(zones, "repo", ActionPlacement.InZone("gone", null))
        assertEquals(
            listOf(
                ActionPlacement.Header,
                ActionPlacement.Footer,
                ActionPlacement.InZone("agents", "claude"),
                ActionPlacement.InZone("agents", "codex"),
                ActionPlacement.InZone("gone", null)
            ),
            repo
        )
    }
}
